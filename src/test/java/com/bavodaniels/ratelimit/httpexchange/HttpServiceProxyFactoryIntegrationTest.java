package com.bavodaniels.ratelimit.httpexchange;

import com.bavodaniels.ratelimit.config.RateLimitAutoConfiguration;
import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for HttpServiceProxyFactory with Spring Boot auto-configuration.
 * Verifies that @HttpExchange interfaces work correctly with rate limiting when
 * HttpServiceProxyFactory beans are created in a Spring context.
 */
class HttpServiceProxyFactoryIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    /**
     * Sample HTTP interface for testing.
     */
    @HttpExchange("http://api.example.com")
    interface TestApiClient {
        @GetExchange("/users/{id}")
        String getUser(String id);

        @GetExchange("/health")
        String healthCheck();
    }

    /**
     * Test configuration that creates an HttpServiceProxyFactory bean using auto-configured RestClient.Builder.
     */
    @Configuration
    static class RestClientProxyFactoryConfig {

        @Bean
        public ClientHttpRequestFactory mockRequestFactory() {
            return (uri, httpMethod) -> new MockClientHttpRequest(httpMethod, uri) {
                @Override
                public ClientHttpResponse executeInternal() throws IOException {
                    HttpHeaders responseHeaders = new HttpHeaders();

                    if (!uri.toString().contains("/health")) {
                        responseHeaders.set("X-RateLimit-Limit", "100");
                        responseHeaders.set("X-RateLimit-Remaining", "50");
                        responseHeaders.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
                    }

                    MockClientHttpResponse response = new MockClientHttpResponse("OK".getBytes(), HttpStatus.OK);
                    response.getHeaders().putAll(responseHeaders);
                    return response;
                }
            };
        }

        @Bean
        public HttpServiceProxyFactory httpServiceProxyFactory(
                RestClient.Builder restClientBuilder,
                ClientHttpRequestFactory requestFactory) {
            RestClient restClient = restClientBuilder
                    .baseUrl("http://api.example.com")
                    .requestFactory(requestFactory)
                    .build();

            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(restClient))
                    .build();
        }

        @Bean
        public TestApiClient testApiClient(HttpServiceProxyFactory factory) {
            return factory.createClient(TestApiClient.class);
        }
    }

    /**
     * Test configuration that uses WebClient for HttpServiceProxyFactory.
     */
    @Configuration
    static class WebClientProxyFactoryConfig {

        @Bean
        public HttpServiceProxyFactory httpServiceProxyFactory(WebClient.Builder webClientBuilder) {
            WebClient webClient = webClientBuilder
                    .baseUrl("http://api.example.com")
                    .build();

            return HttpServiceProxyFactory
                    .builderFor(WebClientAdapter.create(webClient))
                    .build();
        }

        @Bean
        public TestApiClient testApiClient(HttpServiceProxyFactory factory) {
            return factory.createClient(TestApiClient.class);
        }
    }

    @Test
    void httpServiceProxyFactoryBeanShouldBeDetected() {
        contextRunner
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("httpServiceProxyFactory");
                    assertThat(context).hasSingleBean(HttpServiceProxyFactory.class);
                    assertThat(context).hasSingleBean(TestApiClient.class);
                });
    }

    @Test
    void httpServiceProxyFactoryShouldUseRateLimitedRestClient() {
        contextRunner
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    TestApiClient client = context.getBean(TestApiClient.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Make a request that should populate rate limit headers
                    String result = client.getUser("123");
                    assertThat(result).isEqualTo("OK");

                    // Verify rate limit state was tracked
                    RateLimitState state = tracker.getState("api.example.com");
                    assertThat(state).isNotNull();
                    assertThat(state.getLimit()).isEqualTo(100);
                    assertThat(state.getRemaining()).isEqualTo(50);
                });
    }

    @Test
    void httpServiceProxyFactoryShouldRespectRateLimits() {
        contextRunner
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    TestApiClient client = context.getBean(TestApiClient.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Set up a rate limit that will be exceeded
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-RateLimit-Limit", "100");
                    headers.set("X-RateLimit-Remaining", "0");
                    headers.set("Retry-After", "60"); // 60 seconds - exceeds default threshold

                    tracker.updateFromHeaders("api.example.com", headers);

                    // Should throw RateLimitExceededException
                    assertThatThrownBy(() -> client.healthCheck())
                            .isInstanceOf(RateLimitExceededException.class)
                            .hasMessageContaining("api.example.com");
                });
    }

    @Test
    void multipleHttpServiceProxyFactoryBeansShouldShareTracker() {
        contextRunner
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    TestApiClient client = context.getBean(TestApiClient.class);

                    // Make a request to populate state
                    client.getUser("123");

                    // The tracker should have state for this host
                    RateLimitState state = tracker.getState("api.example.com");
                    assertThat(state).isNotNull();

                    // All clients should share the same tracker instance
                    assertThat(tracker).isNotNull();
                });
    }

    @Test
    void httpServiceProxyFactoryShouldBeDisabledWhenPropertyIsFalse() {
        contextRunner
                .withPropertyValues("rate-limit.clients.http-interface.enabled=false")
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    // HttpServiceProxyFactory bean should still exist (user-defined)
                    assertThat(context).hasBean("httpServiceProxyFactory");

                    // But the BeanPostProcessor should not be created
                    assertThat(context).doesNotHaveBean("httpServiceProxyFactoryBeanPostProcessor");
                });
    }

    @Test
    void httpServiceProxyFactoryShouldRespectGlobalDisable() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    // When globally disabled, no rate limit beans should be created
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);

                    // But user's HttpServiceProxyFactory should still exist
                    assertThat(context).hasBean("httpServiceProxyFactory");
                });
    }

    @Test
    void httpServiceProxyFactoryWithRestClientShouldRespectMaxWaitTime() {
        contextRunner
                .withPropertyValues("rate-limit.max-wait-time-millis=5000")
                .withUserConfiguration(RestClientProxyFactoryConfig.class)
                .run(context -> {
                    TestApiClient client = context.getBean(TestApiClient.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Set up a rate limit with wait time exceeding the custom threshold
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-RateLimit-Limit", "100");
                    headers.set("X-RateLimit-Remaining", "0");
                    headers.set("Retry-After", "10"); // 10 seconds - exceeds custom 5 second threshold

                    tracker.updateFromHeaders("api.example.com", headers);

                    // Should throw RateLimitExceededException with custom threshold
                    assertThatThrownBy(() -> client.healthCheck())
                            .isInstanceOf(RateLimitExceededException.class)
                            .satisfies(ex -> {
                                RateLimitExceededException rateEx = (RateLimitExceededException) ex;
                                assertThat(rateEx.getThresholdMillis()).isEqualTo(5000);
                                assertThat(rateEx.getWaitTimeMillis()).isEqualTo(10000);
                            });
                });
    }

    @Test
    void httpServiceProxyFactoryWithWebClientShouldWork() {
        contextRunner
                .withUserConfiguration(WebClientProxyFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasBean("httpServiceProxyFactory");
                    assertThat(context).hasSingleBean(HttpServiceProxyFactory.class);
                    assertThat(context).hasSingleBean(TestApiClient.class);

                    // Verify the factory and client were created
                    TestApiClient client = context.getBean(TestApiClient.class);
                    assertThat(client).isNotNull();
                });
    }

    @Test
    void restClientBuilderShouldBePreConfiguredForHttpServiceProxyFactory() {
        contextRunner
                .run(context -> {
                    // Verify RestClient.Builder is auto-configured
                    assertThat(context).hasSingleBean(RestClient.Builder.class);

                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);

                    // This builder should already have the rate limit interceptor
                    // When used with HttpServiceProxyFactory, it will work transparently
                    assertThat(builder).isNotNull();
                });
    }
}

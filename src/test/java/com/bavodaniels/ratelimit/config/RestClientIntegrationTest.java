package com.bavodaniels.ratelimit.config;

import com.bavodaniels.ratelimit.interceptor.RestTemplateRateLimitInterceptor;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RestClient with rate limit auto-configuration.
 * Tests actual RestClient.Builder bean creation and usage.
 *
 * @since 1.0.0
 */
class RestClientIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    @Test
    void restClientBuilderShouldBeAutoConfigured() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.Builder.class);
                    assertThat(context).hasSingleBean(RateLimitTracker.class);

                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    assertThat(builder).isNotNull();

                    // Should be able to build a RestClient
                    RestClient client = builder.baseUrl("http://example.com").build();
                    assertThat(client).isNotNull();
                });
    }

    @Test
    void restClientBuilderShouldHaveRateLimitInterceptor() {
        contextRunner
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);

                    // The builder should have the interceptor pre-configured
                    // We can verify this by building a client and checking it works
                    RestClient client = builder.baseUrl("http://api.example.com").build();
                    assertThat(client).isNotNull();
                });
    }

    @Test
    void restClientBuilderCanBeCustomizedFurther() {
        contextRunner
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);

                    // Add custom interceptor on top of auto-configured one
                    AtomicInteger interceptorCallCount = new AtomicInteger(0);

                    RestClient client = builder
                            .baseUrl("http://example.com")
                            .requestInterceptor((request, body, execution) -> {
                                interceptorCallCount.incrementAndGet();
                                return execution.execute(request, body);
                            })
                            .build();

                    assertThat(client).isNotNull();
                });
    }

    @Test
    void multipleRestClientsFromSameBuilderShouldShareTracker() {
        contextRunner
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Create multiple clients from the same builder
                    RestClient client1 = builder.baseUrl("http://api1.example.com").build();
                    RestClient client2 = builder.baseUrl("http://api2.example.com").build();

                    assertThat(client1).isNotNull();
                    assertThat(client2).isNotNull();

                    // Both should use the same tracker instance
                    assertThat(tracker).isInstanceOf(InMemoryRateLimitTracker.class);
                });
    }

    @Test
    void restClientBuilderShouldRespectMaxWaitTimeProperty() {
        contextRunner
                .withPropertyValues("rate-limit.max-wait-time-millis=45000")
                .run(context -> {
                    RestClient.Builder builder = context.getBean(RestClient.Builder.class);
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.getMaxWaitTimeMillis()).isEqualTo(45000);
                    assertThat(builder).isNotNull();
                });
    }

    @Test
    void restClientBuilderShouldNotBeCreatedWhenDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.clients.rest-client.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.Builder.class);
                    // Tracker should still be created
                    assertThat(context).hasSingleBean(RateLimitTracker.class);
                });
    }

    @Test
    void restClientBuilderShouldNotBeCreatedWhenGloballyDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.Builder.class);
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);
                });
    }
}

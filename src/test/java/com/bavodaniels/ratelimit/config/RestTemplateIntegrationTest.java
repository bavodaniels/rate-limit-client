package com.bavodaniels.ratelimit.config;

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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for RestTemplate with rate limiting.
 * Tests the full integration using Spring Boot auto-configuration and mock HTTP servers.
 *
 * <p>Test scenarios covered:
 * <ul>
 *   <li>Happy path with rate limit headers</li>
 *   <li>Blocking behavior when wait time is below threshold</li>
 *   <li>Exception throwing when wait time exceeds threshold</li>
 *   <li>Multiple concurrent requests</li>
 *   <li>Different header formats (GitHub, Twitter, Stripe, Retry-After)</li>
 *   <li>Missing/malformed headers</li>
 *   <li>Security considerations (input validation)</li>
 * </ul>
 *
 * @since 1.0.0
 */
class RestTemplateIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    /**
     * Test configuration that creates a RestTemplate bean with a mock request factory.
     */
    @Configuration
    static class RestTemplateTestConfig {

        @Bean
        public ClientHttpRequestFactory mockRequestFactory() {
            return new MockRequestFactory();
        }

        @Bean
        public RestTemplate restTemplate(ClientHttpRequestFactory requestFactory) {
            return new RestTemplate(requestFactory);
        }

        /**
         * Mock request factory that can be configured to return specific responses.
         */
        static class MockRequestFactory implements ClientHttpRequestFactory {
            private volatile MockResponseConfig responseConfig = new MockResponseConfig();

            @Override
            public MockClientHttpRequest createRequest(java.net.URI uri, HttpMethod httpMethod) throws IOException {
                return new MockClientHttpRequest(httpMethod, uri) {
                    @Override
                    public ClientHttpResponse executeInternal() throws IOException {
                        HttpHeaders headers = responseConfig.headers != null
                                ? responseConfig.headers
                                : new HttpHeaders();

                        byte[] body = responseConfig.body != null
                                ? responseConfig.body
                                : "OK".getBytes();

                        HttpStatus status = responseConfig.status != null
                                ? responseConfig.status
                                : HttpStatus.OK;

                        MockClientHttpResponse response = new MockClientHttpResponse(body, status);
                        response.getHeaders().putAll(headers);
                        return response;
                    }
                };
            }

            public void setResponseConfig(MockResponseConfig config) {
                this.responseConfig = config;
            }
        }

        static class MockResponseConfig {
            HttpHeaders headers;
            byte[] body;
            HttpStatus status;
        }
    }

    @Test
    void restTemplateShouldBeAutoConfigured() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestTemplate.class);
                    assertThat(context).hasSingleBean(RateLimitTracker.class);

                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    assertThat(restTemplate).isNotNull();
                    assertThat(restTemplate.getInterceptors()).isNotEmpty();
                });
    }

    @Test
    void restTemplateShouldHandleRequestsWithoutRateLimitHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Make a request without rate limit headers
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");

                    // State should still exist but have default values
                    RateLimitState state = tracker.getState("api.example.com");
                    assertThat(state).isNotNull();
                });
    }

    @Test
    void restTemplateShouldTrackStandardRateLimitHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with standard X-RateLimit headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "75");
                    config.headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
                    factory.setResponseConfig(config);

                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");

                    RateLimitState state = tracker.getState("api.example.com");
                    assertThat(state.getLimit()).isEqualTo(100);
                    assertThat(state.getRemaining()).isEqualTo(75);
                });
    }

    @Test
    void restTemplateShouldTrackGitHubRateLimitHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with GitHub-style headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "5000");
                    config.headers.set("X-RateLimit-Remaining", "4999");
                    config.headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
                    config.headers.set("X-RateLimit-Resource", "core");
                    config.headers.set("X-RateLimit-Used", "1");
                    factory.setResponseConfig(config);

                    String result = restTemplate.getForObject("http://api.github.com/user", String.class);

                    assertThat(result).isEqualTo("OK");

                    RateLimitState state = tracker.getState("api.github.com");
                    assertThat(state.getLimit()).isEqualTo(5000);
                    assertThat(state.getRemaining()).isEqualTo(4999);
                });
    }

    @Test
    void restTemplateShouldTrackStripeRateLimitHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with Stripe-style headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("Stripe-RateLimit-Limit", "100");
                    config.headers.set("Stripe-RateLimit-Remaining", "99");
                    config.headers.set("Stripe-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
                    factory.setResponseConfig(config);

                    String result = restTemplate.getForObject("http://api.stripe.com/v1/charges", String.class);

                    assertThat(result).isEqualTo("OK");

                    RateLimitState state = tracker.getState("api.stripe.com");
                    assertThat(state.getLimit()).isEqualTo(100);
                    assertThat(state.getRemaining()).isEqualTo(99);
                });
    }

    @Test
    void restTemplateShouldHandleRetryAfterWithSeconds() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=3000")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with Retry-After header (seconds format)
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "0");
                    config.headers.set("Retry-After", "1"); // 1 second
                    factory.setResponseConfig(config);

                    // First request populates the rate limit state
                    restTemplate.getForObject("http://api.example.com/test", String.class);

                    // Second request should block for ~1 second
                    long startTime = System.currentTimeMillis();
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    assertThat(result).isEqualTo("OK");
                    assertThat(elapsedTime).isGreaterThanOrEqualTo(900); // Allow some tolerance
                    assertThat(elapsedTime).isLessThan(2000);
                });
    }

    @Test
    void restTemplateShouldHandleRetryAfterWithHttpDate() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=3000")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with Retry-After header (HTTP date format)
                    ZonedDateTime retryTime = ZonedDateTime.now().plusSeconds(1);
                    String httpDate = retryTime.format(DateTimeFormatter.RFC_1123_DATE_TIME);

                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Remaining", "0");
                    config.headers.set("Retry-After", httpDate);
                    factory.setResponseConfig(config);

                    // First request populates the rate limit state
                    restTemplate.getForObject("http://api.example.com/test", String.class);

                    // Second request should block for ~1 second
                    long startTime = System.currentTimeMillis();
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    assertThat(result).isEqualTo("OK");
                    assertThat(elapsedTime).isGreaterThanOrEqualTo(900);
                    assertThat(elapsedTime).isLessThan(2000);
                });
    }

    @Test
    void restTemplateShouldThrowExceptionWhenWaitTimeExceedsThreshold() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=5000")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    // Manually set rate limit state that requires long wait
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-RateLimit-Limit", "100");
                    headers.set("X-RateLimit-Remaining", "0");
                    headers.set("Retry-After", "10"); // 10 seconds exceeds 5 second threshold

                    tracker.updateFromHeaders("api.example.com", headers);

                    // Request should throw exception
                    assertThatThrownBy(() ->
                            restTemplate.getForObject("http://api.example.com/test", String.class))
                            .isInstanceOf(RateLimitExceededException.class)
                            .satisfies(ex -> {
                                RateLimitExceededException rateEx = (RateLimitExceededException) ex;
                                assertThat(rateEx.getHost()).isEqualTo("api.example.com");
                                assertThat(rateEx.getWaitDuration().toMillis()).isEqualTo(10000);
                                assertThat(rateEx.getMessage()).contains("api.example.com");
                                assertThat(rateEx.getMessage()).contains("10 seconds");
                            });
                });
    }

    @Test
    void restTemplateShouldHandleConcurrentRequests() throws InterruptedException {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    AtomicInteger remainingCounter = new AtomicInteger(100);

                    // Configure factory to return decreasing remaining counts
                    factory.setResponseConfig(new RestTemplateTestConfig.MockResponseConfig() {{
                        headers = new HttpHeaders();
                        headers.set("X-RateLimit-Limit", "100");
                    }});

                    int threadCount = 10;
                    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                    CountDownLatch startLatch = new CountDownLatch(1);
                    CountDownLatch endLatch = new CountDownLatch(threadCount);
                    List<Future<String>> futures = new ArrayList<>();

                    for (int i = 0; i < threadCount; i++) {
                        futures.add(executor.submit(() -> {
                            try {
                                startLatch.await();
                                return restTemplate.getForObject("http://api.example.com/test", String.class);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } finally {
                                endLatch.countDown();
                            }
                        }));
                    }

                    // Start all threads
                    startLatch.countDown();

                    // Wait for completion
                    assertThat(endLatch.await(10, TimeUnit.SECONDS)).isTrue();

                    // Verify all requests completed successfully
                    for (Future<String> future : futures) {
                        assertThat(future.get()).isEqualTo("OK");
                    }

                    executor.shutdown();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                });
    }

    @Test
    void restTemplateShouldHandleMultipleHostsConcurrently() throws InterruptedException {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "50");
                    factory.setResponseConfig(config);

                    int hostsCount = 5;
                    int requestsPerHost = 3;
                    ExecutorService executor = Executors.newFixedThreadPool(hostsCount * requestsPerHost);
                    List<Future<String>> futures = new ArrayList<>();

                    for (int hostIndex = 0; hostIndex < hostsCount; hostIndex++) {
                        String host = "api" + hostIndex + ".example.com";
                        for (int reqIndex = 0; reqIndex < requestsPerHost; reqIndex++) {
                            futures.add(executor.submit(() ->
                                    restTemplate.getForObject("http://" + host + "/test", String.class)));
                        }
                    }

                    // Wait for all to complete
                    for (Future<String> future : futures) {
                        assertThat(future.get()).isEqualTo("OK");
                    }

                    executor.shutdown();
                    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

                    // Verify each host has its own state
                    for (int i = 0; i < hostsCount; i++) {
                        String host = "api" + i + ".example.com";
                        RateLimitState state = tracker.getState(host);
                        assertThat(state).isNotNull();
                    }
                });
    }

    @Test
    void restTemplateShouldHandleMalformedHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with malformed headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "invalid");
                    config.headers.set("X-RateLimit-Remaining", "not-a-number");
                    config.headers.set("X-RateLimit-Reset", "bad-timestamp");
                    factory.setResponseConfig(config);

                    // Request should still succeed (malformed headers are ignored)
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");
                });
    }

    @Test
    void restTemplateShouldHandleNegativeHeaderValues() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with negative values (invalid)
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "-100");
                    config.headers.set("X-RateLimit-Remaining", "-50");
                    config.headers.set("Retry-After", "-10");
                    factory.setResponseConfig(config);

                    // Request should still succeed (negative values are ignored)
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");
                });
    }

    @Test
    void restTemplateShouldHandleExcessivelyLargeHeaderValues() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with excessively large values
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", String.valueOf(Long.MAX_VALUE));
                    config.headers.set("X-RateLimit-Reset", "99999999999"); // Far future timestamp (year ~5138)
                    factory.setResponseConfig(config);

                    // Request should handle large values gracefully
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");
                });
    }

    @Test
    void restTemplateShouldHandleMissingHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with no rate limit headers at all
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    factory.setResponseConfig(config);

                    // Request should succeed normally
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");
                });
    }

    @Test
    void restTemplateShouldHandlePartialHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with only some rate limit headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    // Missing Remaining and Reset headers
                    factory.setResponseConfig(config);

                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    assertThat(result).isEqualTo("OK");

                    RateLimitState state = tracker.getState("api.example.com");
                    assertThat(state.getLimit()).isEqualTo(100);
                    // Remaining should have a default value
                });
    }

    @Test
    void restTemplateShouldHandleHostsWithPorts() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "99");
                    factory.setResponseConfig(config);

                    // Request to host with non-standard port
                    String result = restTemplate.getForObject("http://api.example.com:8080/test", String.class);

                    assertThat(result).isEqualTo("OK");

                    // State should be stored with port
                    RateLimitState state = tracker.getState("api.example.com:8080");
                    assertThat(state.getLimit()).isEqualTo(100);
                });
    }

    @Test
    void restTemplateShouldNotBeCreatedWhenDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.clients.rest-template.enabled=false")
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);

                    // RestTemplate bean should exist (user-defined) but without rate limit interceptor
                    assertThat(restTemplate).isNotNull();

                    // Should not have the rate limit interceptor added
                    assertThat(context).doesNotHaveBean("restTemplateRateLimitBeanPostProcessor");

                    // Tracker should still be created
                    assertThat(context).hasSingleBean(RateLimitTracker.class);
                });
    }

    @Test
    void restTemplateShouldNotBeConfiguredWhenGloballyDisabled() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    // User's RestTemplate should still exist
                    assertThat(context).hasSingleBean(RestTemplate.class);

                    // But no rate limit beans should be created
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);
                    assertThat(context).doesNotHaveBean("restTemplateRateLimitBeanPostProcessor");
                });
    }

    @Test
    void restTemplateShouldRespectCustomMaxWaitTime() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=2000")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.getMaxWaitTimeMillis()).isEqualTo(2000);
                });
    }

    @Test
    void restTemplateShouldHandleEmptyResponseBody() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "99");
                    config.body = new byte[0]; // Empty body
                    factory.setResponseConfig(config);

                    // Should handle empty response gracefully
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);

                    // Empty body returns null, not empty string
                    assertThat(result).isNull();
                });
    }

    @Test
    void restTemplateShouldTrackTwitterStyleHeaders() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with Twitter/X-style headers
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "900");
                    config.headers.set("X-RateLimit-Remaining", "899");
                    config.headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(900).getEpochSecond()));
                    factory.setResponseConfig(config);

                    String result = restTemplate.getForObject("http://api.twitter.com/2/tweets", String.class);

                    assertThat(result).isEqualTo("OK");

                    RateLimitState state = tracker.getState("api.twitter.com");
                    assertThat(state.getLimit()).isEqualTo(900);
                    assertThat(state.getRemaining()).isEqualTo(899);
                });
    }

    @Test
    void restTemplateShouldHandleZeroRemaining() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=2000")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with zero remaining but short wait
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-Limit", "100");
                    config.headers.set("X-RateLimit-Remaining", "0");
                    config.headers.set("Retry-After", "1"); // 1 second - within threshold
                    factory.setResponseConfig(config);

                    // First request populates the state
                    restTemplate.getForObject("http://api.example.com/test", String.class);

                    // Second request should block but succeed
                    long startTime = System.currentTimeMillis();
                    String result = restTemplate.getForObject("http://api.example.com/test", String.class);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    assertThat(result).isEqualTo("OK");
                    assertThat(elapsedTime).isGreaterThanOrEqualTo(900);
                });
    }

    @Test
    void restTemplateShouldBlockWhenAnyMultiBucketExceeded() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .withPropertyValues("rate-limit.max-wait-time-millis=5000")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with multiple buckets where SessionOrders is exceeded
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-AppDay-Limit", "10000000");
                    config.headers.set("X-RateLimit-AppDay-Remaining", "9999999");
                    config.headers.set("X-RateLimit-AppDay-Reset", String.valueOf(Instant.now().plusSeconds(86400).getEpochSecond()));
                    config.headers.set("X-RateLimit-Session-Limit", "120");
                    config.headers.set("X-RateLimit-Session-Remaining", "75");
                    config.headers.set("X-RateLimit-Session-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
                    config.headers.set("X-RateLimit-SessionOrders-Limit", "1");
                    config.headers.set("X-RateLimit-SessionOrders-Remaining", "0"); // ← This bucket is exceeded
                    config.headers.set("X-RateLimit-SessionOrders-Reset", String.valueOf(Instant.now().plusSeconds(2).getEpochSecond()));
                    factory.setResponseConfig(config);

                    // First request populates the state
                    restTemplate.getForObject("http://api.example.com/orders", String.class);

                    RateLimitState state = tracker.getState("api.example.com");

                    // Verify all buckets are tracked
                    assertThat(state.getAllBuckets()).containsExactlyInAnyOrder("AppDay", "Session", "SessionOrders");

                    // Verify AppDay bucket (not exceeded)
                    assertThat(state.getBucketInfo("AppDay").remaining()).isEqualTo(9999999);
                    assertThat(state.getBucketInfo("AppDay").isLimitExceeded()).isFalse();

                    // Verify Session bucket (not exceeded)
                    assertThat(state.getBucketInfo("Session").remaining()).isEqualTo(75);
                    assertThat(state.getBucketInfo("Session").isLimitExceeded()).isFalse();

                    // Verify SessionOrders bucket (exceeded)
                    assertThat(state.getBucketInfo("SessionOrders").remaining()).isEqualTo(0);
                    assertThat(state.getBucketInfo("SessionOrders").isLimitExceeded()).isTrue();

                    // Overall state should indicate request is blocked
                    assertThat(state.isLimitExceeded()).isTrue();
                    assertThat(state.canMakeRequest(Instant.now())).isFalse();

                    // Second request should block due to SessionOrders bucket
                    long startTime = System.currentTimeMillis();
                    String result = restTemplate.getForObject("http://api.example.com/orders", String.class);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    assertThat(result).isEqualTo("OK");
                    assertThat(elapsedTime).isGreaterThanOrEqualTo(1900); // Should wait approximately 2 seconds
                });
    }

    @Test
    void restTemplateShouldAllowWhenAllMultiBucketsHaveCapacity() {
        contextRunner
                .withUserConfiguration(RestTemplateTestConfig.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
                    RestTemplateTestConfig.MockRequestFactory factory =
                            (RestTemplateTestConfig.MockRequestFactory) context.getBean(ClientHttpRequestFactory.class);

                    // Configure response with multiple buckets, all with capacity
                    RestTemplateTestConfig.MockResponseConfig config = new RestTemplateTestConfig.MockResponseConfig();
                    config.headers = new HttpHeaders();
                    config.headers.set("X-RateLimit-AppDay-Limit", "10000000");
                    config.headers.set("X-RateLimit-AppDay-Remaining", "9999999");
                    config.headers.set("X-RateLimit-AppDay-Reset", String.valueOf(Instant.now().plusSeconds(86400).getEpochSecond()));
                    config.headers.set("X-RateLimit-Session-Limit", "120");
                    config.headers.set("X-RateLimit-Session-Remaining", "75");
                    config.headers.set("X-RateLimit-Session-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
                    factory.setResponseConfig(config);

                    // First request
                    restTemplate.getForObject("http://api.example.com/data", String.class);

                    RateLimitState state = tracker.getState("api.example.com");

                    // Verify all buckets are tracked
                    assertThat(state.getAllBuckets()).containsExactlyInAnyOrder("AppDay", "Session");

                    // Overall state should allow requests
                    assertThat(state.isLimitExceeded()).isFalse();
                    assertThat(state.canMakeRequest(Instant.now())).isTrue();
                    assertThat(state.getWaitTimeSeconds(Instant.now())).isEqualTo(0);

                    // Second request should succeed without waiting
                    long startTime = System.currentTimeMillis();
                    String result = restTemplate.getForObject("http://api.example.com/data", String.class);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    assertThat(result).isEqualTo("OK");
                    assertThat(elapsedTime).isLessThan(100); // Should not wait
                });
    }
}

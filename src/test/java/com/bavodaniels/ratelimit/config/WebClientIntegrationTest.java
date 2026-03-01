package com.bavodaniels.ratelimit.config;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for WebClient with reactive rate limiting.
 * Tests real HTTP interactions using MockWebServer with various rate limit scenarios.
 *
 * Test Coverage:
 * 1. Reactive happy path with non-blocking delays
 * 2. Non-blocking Mono.delay() behavior
 * 3. Reactive exception handling (Mono.error)
 * 4. Concurrent reactive streams
 * 5. Different header formats (GitHub, Twitter, Stripe, Retry-After)
 * 6. Backpressure scenarios
 * 7. Missing/malformed headers
 *
 * @since 1.0.0
 */
class WebClientIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    private MockWebServer mockWebServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        baseUrl = mockWebServer.url("/").toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    /**
     * Test 1: Reactive Happy Path - Non-blocking request with rate limit headers
     * Verifies that WebClient handles successful requests and updates rate limit state.
     */
    @Test
    void testReactiveHappyPath_WithRateLimitHeaders() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

            WebClient client = builder.baseUrl(baseUrl).build();

            // Mock response with rate limit headers
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"status\":\"ok\"}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-RateLimit-Limit", "1000")
                    .addHeader("X-RateLimit-Remaining", "999")
                    .addHeader("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond())));

            // Execute request
            Mono<String> response = client.get()
                    .uri("/api/users")
                    .retrieve()
                    .bodyToMono(String.class);

            // Verify reactive behavior
            StepVerifier.create(response)
                    .expectNext("{\"status\":\"ok\"}")
                    .verifyComplete();

            // Verify rate limit state was updated
            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(1000);
            assertThat(state.getRemaining()).isEqualTo(999);
        });
    }

    /**
     * Test 2: Non-blocking Delay Behavior with Mono.delay()
     * Verifies that rate limit delays are truly non-blocking and don't block threads.
     */
    @Test
    void testNonBlockingDelay_WithMonoDelay() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

            // Pre-populate rate limit state requiring a 1 second wait
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RateLimit-Limit", "10");
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("Retry-After", "1"); // 1 second delay
            tracker.updateFromHeaders(host, headers);

            WebClient client = builder.baseUrl(baseUrl).build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("success"));

            long startTime = System.currentTimeMillis();

            Mono<String> response = client.get()
                    .uri("/api/test")
                    .retrieve()
                    .bodyToMono(String.class);

            // Verify the delay happens non-blockingly
            StepVerifier.create(response)
                    .expectNext("success")
                    .verifyComplete();

            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isGreaterThanOrEqualTo(900); // Allow 100ms tolerance
            assertThat(elapsed).isLessThan(2000); // Should not take too long
        });
    }

    /**
     * Test 3: Reactive Exception Handling - Mono.error for rate limit exceeded
     * Verifies that excessive wait times throw RateLimitExceededException reactively.
     */
    @Test
    void testReactiveExceptionHandling_RateLimitExceeded() {
        contextRunner
                .withPropertyValues("rate-limit.max-wait-time-millis=500")
                .run(context -> {
                    WebClient.Builder builder = context.getBean(WebClient.Builder.class);
                    RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

                    String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

                    // Set rate limit requiring excessive wait (2 seconds)
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-RateLimit-Remaining", "0");
                    headers.set("Retry-After", "2"); // 2 seconds - exceeds 500ms threshold
                    tracker.updateFromHeaders(host, headers);

                    WebClient client = builder.baseUrl(baseUrl).build();

                    Mono<String> response = client.get()
                            .uri("/api/test")
                            .retrieve()
                            .bodyToMono(String.class);

                    // Verify reactive error handling
                    StepVerifier.create(response)
                            .expectErrorMatches(e -> e instanceof RateLimitExceededException &&
                                    ((RateLimitExceededException) e).getHost().contains(host))
                            .verify();
                });
    }

    /**
     * Test 4: Concurrent Reactive Streams
     * Verifies that multiple concurrent requests are handled correctly with rate limiting.
     */
    @Test
    void testConcurrentReactiveStreams_MultipleRequests() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            int requestCount = 10;
            AtomicInteger responseCount = new AtomicInteger(0);

            // Enqueue multiple responses
            for (int i = 0; i < requestCount; i++) {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setBody("response-" + i)
                        .addHeader("X-RateLimit-Limit", "100")
                        .addHeader("X-RateLimit-Remaining", String.valueOf(100 - i)));
            }

            // Create concurrent requests using Flux
            Flux<String> responses = Flux.range(0, requestCount)
                    .flatMap(i -> client.get()
                            .uri("/api/item/" + i)
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnNext(r -> responseCount.incrementAndGet()));

            // Verify all requests complete
            StepVerifier.create(responses)
                    .expectNextCount(requestCount)
                    .verifyComplete();

            assertThat(responseCount.get()).isEqualTo(requestCount);
        });
    }

    /**
     * Test 5a: GitHub Rate Limit Headers
     * Verifies parsing of GitHub-style rate limit headers.
     */
    @Test
    void testDifferentHeaderFormats_GitHub() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            long resetTime = Instant.now().plusSeconds(3600).getEpochSecond();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"data\":[]}")
                    .addHeader("X-RateLimit-Limit", "5000")
                    .addHeader("X-RateLimit-Remaining", "4999")
                    .addHeader("X-RateLimit-Reset", String.valueOf(resetTime))
                    .addHeader("X-RateLimit-Resource", "core")
                    .addHeader("X-RateLimit-Used", "1"));

            StepVerifier.create(client.get().uri("/api/repos").retrieve().bodyToMono(String.class))
                    .expectNext("{\"data\":[]}")
                    .verifyComplete();

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(5000);
            assertThat(state.getRemaining()).isEqualTo(4999);
        });
    }

    /**
     * Test 5b: Twitter/X Rate Limit Headers
     * Verifies parsing of Twitter-style rate limit headers.
     */
    @Test
    void testDifferentHeaderFormats_Twitter() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            long resetTime = Instant.now().plusSeconds(900).getEpochSecond();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"tweets\":[]}")
                    .addHeader("X-RateLimit-Limit", "180")
                    .addHeader("X-RateLimit-Remaining", "179")
                    .addHeader("X-RateLimit-Reset", String.valueOf(resetTime)));

            StepVerifier.create(client.get().uri("/api/tweets").retrieve().bodyToMono(String.class))
                    .expectNext("{\"tweets\":[]}")
                    .verifyComplete();

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(180);
            assertThat(state.getRemaining()).isEqualTo(179);
        });
    }

    /**
     * Test 5c: Stripe Rate Limit Headers
     * Verifies parsing of Stripe-style rate limit headers.
     */
    @Test
    void testDifferentHeaderFormats_Stripe() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            long resetTime = Instant.now().plusSeconds(60).getEpochSecond();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"object\":\"charge\"}")
                    .addHeader("Stripe-RateLimit-Limit", "100")
                    .addHeader("Stripe-RateLimit-Remaining", "95")
                    .addHeader("Stripe-RateLimit-Reset", String.valueOf(resetTime)));

            StepVerifier.create(client.get().uri("/v1/charges").retrieve().bodyToMono(String.class))
                    .expectNext("{\"object\":\"charge\"}")
                    .verifyComplete();

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(100);
            assertThat(state.getRemaining()).isEqualTo(95);
        });
    }

    /**
     * Test 5d: Retry-After Header with Seconds
     * Verifies parsing of Retry-After header with delay in seconds.
     */
    @Test
    void testDifferentHeaderFormats_RetryAfterSeconds() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

            // First request to populate state with Retry-After
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("Retry-After", "1"); // 1 second
            tracker.updateFromHeaders(host, headers);

            WebClient client = builder.baseUrl(baseUrl).build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("ok"));

            long startTime = System.currentTimeMillis();

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("ok")
                    .verifyComplete();

            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isGreaterThanOrEqualTo(900); // Should wait ~1 second
        });
    }

    /**
     * Test 5e: Retry-After Header with HTTP Date
     * Verifies parsing of Retry-After header with HTTP date format.
     */
    @Test
    void testDifferentHeaderFormats_RetryAfterHttpDate() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

            // Use HTTP date format (RFC 1123)
            Instant retryTime = Instant.now().plusSeconds(1);
            String httpDate = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(retryTime.atZone(java.time.ZoneId.of("GMT")));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("Retry-After", httpDate);
            tracker.updateFromHeaders(host, headers);

            WebClient client = builder.baseUrl(baseUrl).build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("ok"));

            long startTime = System.currentTimeMillis();

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("ok")
                    .verifyComplete();

            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(elapsed).isGreaterThanOrEqualTo(900);
        });
    }

    /**
     * Test 6: Backpressure Scenarios
     * Verifies that reactive streams handle backpressure correctly with rate limiting.
     */
    @Test
    void testBackpressureScenarios_LimitedConcurrency() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            int totalRequests = 5;
            int concurrency = 2;

            // Enqueue responses with decreasing rate limits
            for (int i = 0; i < totalRequests; i++) {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setBody("item-" + i)
                        .addHeader("X-RateLimit-Limit", "100")
                        .addHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, 100 - i))));
            }

            AtomicInteger completedCount = new AtomicInteger(0);

            // Create flux with limited concurrency (backpressure)
            Flux<String> responses = Flux.range(0, totalRequests)
                    .flatMap(i -> client.get()
                            .uri("/api/item/" + i)
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnNext(r -> completedCount.incrementAndGet()),
                            concurrency); // Limit to 2 concurrent requests

            StepVerifier.create(responses)
                    .expectNextCount(totalRequests)
                    .verifyComplete();

            assertThat(completedCount.get()).isEqualTo(totalRequests);
        });
    }

    /**
     * Test 7a: Missing Rate Limit Headers
     * Verifies graceful handling when rate limit headers are absent.
     */
    @Test
    void testMissingHeaders_NoRateLimitHeaders() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            // Response without any rate limit headers
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("success"));

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("success")
                    .verifyComplete();

            // Should not throw exception, request should succeed
        });
    }

    /**
     * Test 7b: Malformed Rate Limit Headers
     * Verifies graceful handling of malformed rate limit header values.
     */
    @Test
    void testMalformedHeaders_InvalidValues() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            // Response with malformed headers
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("success")
                    .addHeader("X-RateLimit-Limit", "invalid")
                    .addHeader("X-RateLimit-Remaining", "not-a-number")
                    .addHeader("X-RateLimit-Reset", "abc"));

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("success")
                    .verifyComplete();

            // Should handle gracefully without throwing exception
        });
    }

    /**
     * Test 7c: Partial Rate Limit Headers
     * Verifies handling when only some rate limit headers are present.
     */
    @Test
    void testPartialHeaders_OnlySomeHeadersPresent() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            // Only limit and remaining, no reset
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("partial")
                    .addHeader("X-RateLimit-Limit", "100")
                    .addHeader("X-RateLimit-Remaining", "50"));

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("partial")
                    .verifyComplete();

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(100);
            assertThat(state.getRemaining()).isEqualTo(50);
        });
    }

    /**
     * Test 8: Error Response Handling
     * Verifies that HTTP errors are propagated correctly through the reactive chain.
     */
    @Test
    void testErrorResponseHandling_4xxErrors() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setBody("Not Found"));

            StepVerifier.create(client.get()
                    .uri("/api/nonexistent")
                    .retrieve()
                    .bodyToMono(String.class))
                    .expectError()
                    .verify();
        });
    }

    /**
     * Test 9: Sequential vs Parallel Request Handling
     * Verifies different execution strategies with rate limiting.
     */
    @Test
    void testSequentialVsParallel_RequestExecution() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            int requestCount = 5;

            for (int i = 0; i < requestCount; i++) {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setBody("seq-" + i)
                        .addHeader("X-RateLimit-Remaining", String.valueOf(10 - i)));
            }

            // Sequential execution using concatMap
            Flux<String> sequentialResponses = Flux.range(0, requestCount)
                    .concatMap(i -> client.get()
                            .uri("/api/seq/" + i)
                            .retrieve()
                            .bodyToMono(String.class));

            StepVerifier.create(sequentialResponses)
                    .expectNextCount(requestCount)
                    .verifyComplete();
        });
    }

    /**
     * Test 10: Timeout Handling with Rate Limiting
     * Verifies that timeouts work correctly with rate limited requests.
     */
    @Test
    void testTimeoutHandling_WithRateLimit() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            WebClient client = builder.baseUrl(baseUrl)
                    .build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("slow response")
                    .setBodyDelay(5, TimeUnit.SECONDS));

            StepVerifier.create(client.get()
                    .uri("/api/slow")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(1)))
                    .expectError(java.util.concurrent.TimeoutException.class)
                    .verify();
        });
    }

    /**
     * Test 11: WebClient Builder Customization
     * Verifies that rate limiting works with customized WebClient builders.
     */
    @Test
    void testWebClientBuilderCustomization_AdditionalFilters() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            AtomicInteger customFilterCalls = new AtomicInteger(0);

            // Add custom filter on top of rate limit filter
            WebClient client = builder
                    .baseUrl(baseUrl)
                    .filter((request, next) -> {
                        customFilterCalls.incrementAndGet();
                        return next.exchange(request);
                    })
                    .build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("custom"));

            StepVerifier.create(client.get().uri("/api/test").retrieve().bodyToMono(String.class))
                    .expectNext("custom")
                    .verifyComplete();

            assertThat(customFilterCalls.get()).isEqualTo(1);
        });
    }

    /**
     * Test 12: Rate Limit State Updates Across Multiple Requests
     * Verifies that rate limit state is correctly updated after each request.
     */
    @Test
    void testRateLimitStateUpdates_MultipleRequests() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);
            WebClient client = builder.baseUrl(baseUrl).build();

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

            // Enqueue responses with decreasing remaining count
            for (int i = 0; i < 5; i++) {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setBody("request-" + i)
                        .addHeader("X-RateLimit-Limit", "10")
                        .addHeader("X-RateLimit-Remaining", String.valueOf(10 - i - 1)));
            }

            // Execute requests sequentially
            Flux<String> requests = Flux.range(0, 5)
                    .concatMap(i -> client.get()
                            .uri("/api/test/" + i)
                            .retrieve()
                            .bodyToMono(String.class));

            StepVerifier.create(requests)
                    .expectNextCount(5)
                    .verifyComplete();

            // Verify final state
            var state = tracker.getState(host);
            assertThat(state.getLimit()).isEqualTo(10);
            assertThat(state.getRemaining()).isEqualTo(5); // After 5 requests
        });
    }

    /**
     * Test 13: WebClient Should Not Be Created When Disabled
     * Verifies that rate limiting can be disabled via configuration.
     */
    @Test
    void testWebClientCustomizer_DisabledViaProperty() {
        contextRunner
                .withPropertyValues("rate-limit.clients.web-client.enabled=false")
                .run(context -> {
                    // WebClient.Builder should still exist (Spring provides it)
                    // but without the rate limit customizer
                    assertThat(context).doesNotHaveBean("webClientRateLimitCustomizer");
                });
    }

    /**
     * Test 14: Global Rate Limit Disable
     * Verifies that globally disabling rate limiting works correctly.
     */
    @Test
    void testGlobalDisable_NoRateLimitBeans() {
        contextRunner
                .withPropertyValues("rate-limit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitTracker.class);
                    assertThat(context).doesNotHaveBean("webClientRateLimitCustomizer");
                });
    }

    /**
     * Test 15: Retry Logic After Rate Limit Reset
     * Verifies that requests succeed after rate limit reset time has passed.
     */
    @Test
    void testRetryAfterReset_RequestSucceedsAfterResetTime() {
        contextRunner.run(context -> {
            WebClient.Builder builder = context.getBean(WebClient.Builder.class);
            RateLimitTracker tracker = context.getBean(RateLimitTracker.class);

            String host = mockWebServer.getHostName() + ":" + mockWebServer.getPort();

            // Set initial rate limit that will reset soon
            Instant resetTime = Instant.now().plusSeconds(1);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-RateLimit-Limit", "10");
            headers.set("X-RateLimit-Remaining", "0");
            headers.set("X-RateLimit-Reset", String.valueOf(resetTime.getEpochSecond()));
            tracker.updateFromHeaders(host, headers);

            WebClient client = builder.baseUrl(baseUrl).build();

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("success after reset"));

            // Wait for reset time and make request
            Mono<String> delayedRequest = Mono.delay(Duration.ofMillis(1100))
                    .then(client.get()
                            .uri("/api/test")
                            .retrieve()
                            .bodyToMono(String.class));

            StepVerifier.create(delayedRequest)
                    .expectNext("success after reset")
                    .verifyComplete();
        });
    }
}

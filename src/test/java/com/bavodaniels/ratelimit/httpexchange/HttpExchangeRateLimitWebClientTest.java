package com.bavodaniels.ratelimit.httpexchange;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.filter.WebClientRateLimitFilter;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for @HttpExchange interfaces with WebClient-backed HttpServiceProxyFactory.
 * This demonstrates that rate limiting works transparently with declarative HTTP interfaces
 * when backed by a WebClient with the rate limit filter configured.
 */
class HttpExchangeRateLimitWebClientTest {

    /**
     * Sample HTTP interface for testing with WebClient backend.
     */
    @HttpExchange("http://api.example.com")
    interface ReactiveApiClient {

        @GetExchange("/reactive/data")
        String getData();

        @PostExchange("/reactive/data")
        String postData(String payload);

        @GetExchange("/reactive/ping")
        String ping();
    }

    private RateLimitTracker tracker;
    private ReactiveApiClient apiClient;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        WebClientRateLimitFilter filter = new WebClientRateLimitFilter(tracker, 5000);

        // Create WebClient with rate limit filter
        WebClient webClient = WebClient.builder()
                .exchangeFunction(createMockExchangeFunction())
                .filter(filter)
                .build();

        // Create HttpServiceProxyFactory backed by WebClient
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(webClient))
                .build();

        // Create proxy instance of our API client
        apiClient = factory.createClient(ReactiveApiClient.class);
    }

    @Test
    void testWebClientBackedHttpExchangeWithoutRateLimitHeaders() {
        // Should successfully make a request without rate limit headers
        assertDoesNotThrow(() -> {
            String result = apiClient.ping();
            assertEquals("PONG", result);
        });
    }

    @Test
    void testWebClientBackedHttpExchangeWithRateLimitHeaders() {
        // Make a request that will populate rate limit headers
        assertDoesNotThrow(() -> {
            apiClient.getData();
        });

        // Verify state was updated from headers
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(200, state.getLimit());
        assertEquals(150, state.getRemaining());
    }

    @Test
    void testWebClientBackedPostExchange() {
        // Make a POST request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.postData("{\"reactive\":\"data\"}");
            assertEquals("OK", result);
        });

        // Verify rate limit state was tracked
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
    }

    @Test
    void testWebClientBackedHttpExchangeWithRateLimitExceeded() {
        String host = "api.example.com";

        // Set up a state that requires waiting beyond threshold
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "200");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "10"); // 10 seconds - exceeds our 5 second threshold

        tracker.updateFromHeaders(host, headers);

        // Should throw RateLimitExceededException
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () -> {
            apiClient.ping();
        });

        assertEquals(host, exception.getHost());
        assertEquals(10000, exception.getWaitTimeMillis());
        assertEquals(5000, exception.getThresholdMillis());
    }

    @Test
    void testWebClientBackedHttpExchangeWithShortWait() {
        String host = "api.example.com";

        // Set up a state that requires a short wait (under threshold)
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "200");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(10).getEpochSecond()));
        headers.set("Retry-After", "1"); // 1 second - under threshold

        tracker.updateFromHeaders(host, headers);

        // Should wait approximately 1 second before making the request
        long startTime = System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            apiClient.ping();
        });
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime >= 900, "Should have waited at least 0.9 seconds, but was " + elapsedTime + "ms");
        assertTrue(elapsedTime < 2000, "Should not have waited more than 2 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testConsecutiveRequestsWithWebClient() {
        // Make consecutive requests to verify rate limiting is applied consistently
        assertDoesNotThrow(() -> {
            apiClient.getData();
            apiClient.postData("test");
            apiClient.ping();
        });

        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(200, state.getLimit());
    }

    @Test
    void testReactiveNonBlockingBehavior() {
        String host = "api.example.com";

        // Set up a state that requires a 1 second wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "200");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "1"); // 1 second wait

        tracker.updateFromHeaders(host, headers);

        // The wait should be non-blocking in the reactive implementation
        // This test verifies it doesn't throw and completes in reasonable time
        long startTime = System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            apiClient.getData();
        });
        long elapsedTime = System.currentTimeMillis() - startTime;

        // Should complete with the reactive delay
        assertTrue(elapsedTime >= 900, "Request should have delayed, elapsed: " + elapsedTime + "ms");
    }

    // Helper method to create a mock exchange function for testing
    private ExchangeFunction createMockExchangeFunction() {
        return request -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            String body = "OK";

            // Determine response based on URI path
            String path = request.url().getPath();
            if (path.contains("/ping")) {
                body = "PONG";
            } else {
                // Add rate limit headers for data endpoints
                responseHeaders.set("X-RateLimit-Limit", "200");
                responseHeaders.set("X-RateLimit-Remaining", "150");
                responseHeaders.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
            }

            ClientResponse response = ClientResponse
                    .create(HttpStatus.OK)
                    .headers(headers -> headers.addAll(responseHeaders))
                    .body(body)
                    .build();

            return Mono.just(response);
        };
    }
}

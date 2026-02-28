package com.bavodaniels.ratelimit.interceptor;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RestClient with RestTemplateRateLimitInterceptor.
 * Demonstrates that the same interceptor can be used for both RestTemplate and RestClient
 * since they share the ClientHttpRequestInterceptor interface.
 */
class RestClientRateLimitInterceptorTest {

    private RateLimitTracker tracker;
    private RestTemplateRateLimitInterceptor interceptor;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000); // 5 second threshold

        // Create a RestClient with the rate limit interceptor
        restClient = RestClient.builder()
                .requestFactory(createMockRequestFactory())
                .requestInterceptor(interceptor)
                .build();
    }

    @Test
    void testRestClientWithNoRateLimitHeaders() {
        // Should successfully make a request without rate limit headers
        assertDoesNotThrow(() -> {
            String result = restClient.get()
                    .uri("http://api.example.com/test")
                    .retrieve()
                    .body(String.class);
            assertEquals("OK", result);
        });
    }

    @Test
    void testRestClientWithRateLimitHeaders() {
        // Make a request that will populate rate limit headers
        assertDoesNotThrow(() -> {
            restClient.get()
                    .uri("http://api.example.com/with-headers")
                    .retrieve()
                    .body(String.class);
        });

        // Verify state was updated from headers
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    @Test
    void testRestClientWithRateLimitExceeded() {
        String host = "api.example.com";

        // Set up a state that requires waiting beyond threshold
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "10"); // 10 seconds - exceeds our 5 second threshold

        tracker.updateFromHeaders(host, headers);

        // Should throw RateLimitExceededException
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () -> {
            restClient.get()
                    .uri("http://" + host + "/test")
                    .retrieve()
                    .body(String.class);
        });

        assertEquals(host, exception.getHost());
        assertEquals(10000, exception.getWaitTimeMillis());
        assertEquals(5000, exception.getThresholdMillis());
    }

    @Test
    void testRestClientWithShortWait() {
        String host = "api.example.com";

        // Set up a state that requires a short wait (under threshold)
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(10).getEpochSecond()));
        headers.set("Retry-After", "2"); // 2 seconds - under threshold

        tracker.updateFromHeaders(host, headers);

        // Should wait approximately 2 seconds before making the request
        long startTime = System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            restClient.get()
                    .uri("http://" + host + "/test")
                    .retrieve()
                    .body(String.class);
        });
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime >= 1800, "Should have waited at least 1.8 seconds, but was " + elapsedTime + "ms");
        assertTrue(elapsedTime < 3000, "Should not have waited more than 3 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testRestClientBuilderWithInterceptor() {
        // Verify that the interceptor is properly configured
        assertNotNull(interceptor);
        assertEquals(tracker, interceptor.getRateLimitTracker());
        assertEquals(5000, interceptor.getMaxWaitTimeMillis());
    }

    @Test
    void testRestClientWithCustomPort() {
        assertDoesNotThrow(() -> {
            restClient.get()
                    .uri("http://api.example.com:8080/test")
                    .retrieve()
                    .body(String.class);
        });

        // Verify state was stored with port
        RateLimitState state = tracker.getState("api.example.com:8080");
        assertNotNull(state);
    }

    // Helper method to create a mock request factory for testing
    private ClientHttpRequestFactory createMockRequestFactory() {
        return (uri, httpMethod) -> new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
            @Override
            public ClientHttpResponse executeInternal() throws IOException {
                HttpHeaders responseHeaders = new HttpHeaders();

                // Add rate limit headers for specific test URIs
                if (uri.toString().contains("with-headers")) {
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
}

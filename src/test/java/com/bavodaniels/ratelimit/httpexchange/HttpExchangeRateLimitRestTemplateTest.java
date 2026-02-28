package com.bavodaniels.ratelimit.httpexchange;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.interceptor.RestTemplateRateLimitInterceptor;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.support.RestTemplateAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for @HttpExchange interfaces with RestTemplate-backed HttpServiceProxyFactory.
 * This demonstrates that rate limiting works transparently with declarative HTTP interfaces
 * when backed by a RestTemplate with the rate limit interceptor configured.
 */
class HttpExchangeRateLimitRestTemplateTest {

    /**
     * Sample HTTP interface for testing with RestTemplate backend.
     */
    @HttpExchange("http://api.example.com")
    interface ApiClient {

        @GetExchange("/data")
        String getData();

        @PostExchange("/data")
        String postData(String payload);

        @GetExchange("/status")
        String getStatus();
    }

    private RateLimitTracker tracker;
    private ApiClient apiClient;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        RestTemplateRateLimitInterceptor interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000);

        // Create RestTemplate with rate limit interceptor
        RestTemplate restTemplate = new RestTemplate(createMockRequestFactory());
        restTemplate.getInterceptors().add(interceptor);

        // Create HttpServiceProxyFactory backed by RestTemplate
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestTemplateAdapter.create(restTemplate))
                .build();

        // Create proxy instance of our API client
        apiClient = factory.createClient(ApiClient.class);
    }

    @Test
    void testRestTemplateBackedHttpExchangeWithoutRateLimitHeaders() {
        // Should successfully make a request without rate limit headers
        assertDoesNotThrow(() -> {
            String result = apiClient.getStatus();
            assertEquals("OK", result);
        });
    }

    @Test
    void testRestTemplateBackedHttpExchangeWithRateLimitHeaders() {
        // Make a request that will populate rate limit headers
        assertDoesNotThrow(() -> {
            apiClient.getData();
        });

        // Verify state was updated from headers
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.getLimit());
        assertEquals(75, state.getRemaining());
    }

    @Test
    void testRestTemplateBackedPostExchange() {
        // Make a POST request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.postData("{\"test\":\"data\"}");
            assertEquals("OK", result);
        });

        // Verify rate limit state was tracked
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
    }

    @Test
    void testRestTemplateBackedHttpExchangeWithRateLimitExceeded() {
        String host = "api.example.com";

        // Set up a state that requires waiting beyond threshold
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "10"); // 10 seconds - exceeds our 5 second threshold

        tracker.updateFromHeaders(host, headers);

        // Should throw RateLimitExceededException
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () -> {
            apiClient.getStatus();
        });

        assertEquals(host, exception.getHost());
        assertEquals(10000, exception.getWaitTimeMillis());
        assertEquals(5000, exception.getThresholdMillis());
    }

    @Test
    void testRestTemplateBackedHttpExchangeWithShortWait() {
        String host = "api.example.com";

        // Set up a state that requires a short wait (under threshold)
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(10).getEpochSecond()));
        headers.set("Retry-After", "1"); // 1 second - under threshold

        tracker.updateFromHeaders(host, headers);

        // Should wait approximately 1 second before making the request
        long startTime = System.currentTimeMillis();
        assertDoesNotThrow(() -> {
            apiClient.getStatus();
        });
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime >= 900, "Should have waited at least 0.9 seconds, but was " + elapsedTime + "ms");
        assertTrue(elapsedTime < 2000, "Should not have waited more than 2 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testConsecutiveRequestsWithRestTemplate() {
        // Make consecutive requests to verify rate limiting is applied consistently
        assertDoesNotThrow(() -> {
            apiClient.getData();
            apiClient.postData("test");
            apiClient.getStatus();
        });

        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.getLimit());
    }

    // Helper method to create a mock request factory for testing
    private ClientHttpRequestFactory createMockRequestFactory() {
        return (uri, httpMethod) -> new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
            @Override
            public ClientHttpResponse executeInternal() throws IOException {
                HttpHeaders responseHeaders = new HttpHeaders();

                // Add rate limit headers for all requests except /status
                if (!uri.toString().contains("/status")) {
                    responseHeaders.set("X-RateLimit-Limit", "100");
                    responseHeaders.set("X-RateLimit-Remaining", "75");
                    responseHeaders.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
                }

                MockClientHttpResponse response = new MockClientHttpResponse("OK".getBytes(), HttpStatus.OK);
                response.getHeaders().putAll(responseHeaders);
                return response;
            }
        };
    }
}

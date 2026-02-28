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
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for @HttpExchange interfaces with RestClient-backed HttpServiceProxyFactory.
 * This demonstrates that rate limiting works transparently with declarative HTTP interfaces
 * when backed by a RestClient with the rate limit interceptor configured.
 */
class HttpExchangeRateLimitRestClientTest {

    /**
     * Sample HTTP interface demonstrating all @HttpExchange annotation types.
     * This interface will be proxied by HttpServiceProxyFactory.
     */
    @HttpExchange("http://api.example.com")
    interface SampleApiClient {

        @GetExchange("/users/{id}")
        String getUser(String id);

        @PostExchange("/users")
        String createUser(String userData);

        @PutExchange("/users/{id}")
        String updateUser(String id, String userData);

        @PatchExchange("/users/{id}")
        String patchUser(String id, String partialData);

        @DeleteExchange("/users/{id}")
        String deleteUser(String id);

        @GetExchange("/health")
        String healthCheck();
    }

    private RateLimitTracker tracker;
    private SampleApiClient apiClient;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        RestTemplateRateLimitInterceptor interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000);

        // Create RestClient with rate limit interceptor
        RestClient restClient = RestClient.builder()
                .requestFactory(createMockRequestFactory())
                .requestInterceptor(interceptor)
                .build();

        // Create HttpServiceProxyFactory backed by RestClient
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClient.RestClientAdapter.create(restClient))
                .build();

        // Create proxy instance of our API client
        apiClient = factory.createClient(SampleApiClient.class);
    }

    @Test
    void testGetExchangeWithoutRateLimitHeaders() {
        // Should successfully make a GET request without rate limit headers
        assertDoesNotThrow(() -> {
            String result = apiClient.healthCheck();
            assertEquals("OK", result);
        });
    }

    @Test
    void testGetExchangeWithRateLimitHeaders() {
        // Make a request that will populate rate limit headers
        assertDoesNotThrow(() -> {
            apiClient.getUser("123");
        });

        // Verify state was updated from headers
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    @Test
    void testPostExchangeWithRateLimit() {
        // Make a POST request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.createUser("{\"name\":\"John\"}");
            assertEquals("OK", result);
        });

        // Verify rate limit state was tracked
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
    }

    @Test
    void testPutExchangeWithRateLimit() {
        // Make a PUT request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.updateUser("123", "{\"name\":\"Jane\"}");
            assertEquals("OK", result);
        });
    }

    @Test
    void testPatchExchangeWithRateLimit() {
        // Make a PATCH request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.patchUser("123", "{\"status\":\"active\"}");
            assertEquals("OK", result);
        });
    }

    @Test
    void testDeleteExchangeWithRateLimit() {
        // Make a DELETE request through the HTTP interface
        assertDoesNotThrow(() -> {
            String result = apiClient.deleteUser("123");
            assertEquals("OK", result);
        });
    }

    @Test
    void testHttpExchangeWithRateLimitExceeded() {
        String host = "api.example.com";

        // Set up a state that requires waiting beyond threshold
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "10"); // 10 seconds - exceeds our 5 second threshold

        tracker.updateFromHeaders(host, headers);

        // Should throw RateLimitExceededException when calling any method
        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () -> {
            apiClient.healthCheck();
        });

        assertEquals(host, exception.getHost());
        assertEquals(10000, exception.getWaitTimeMillis());
        assertEquals(5000, exception.getThresholdMillis());
    }

    @Test
    void testHttpExchangeWithShortWait() {
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
            apiClient.healthCheck();
        });
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertTrue(elapsedTime >= 1800, "Should have waited at least 1.8 seconds, but was " + elapsedTime + "ms");
        assertTrue(elapsedTime < 3000, "Should not have waited more than 3 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testMultipleRequestsUpdateRateLimitState() {
        // Make multiple requests and verify state is tracked
        assertDoesNotThrow(() -> {
            apiClient.healthCheck();
            apiClient.getUser("123");
            apiClient.createUser("{\"name\":\"Test\"}");
        });

        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        // State should reflect the most recent response
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    // Helper method to create a mock request factory for testing
    private ClientHttpRequestFactory createMockRequestFactory() {
        return (uri, httpMethod) -> new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
            @Override
            public ClientHttpResponse executeInternal() throws IOException {
                HttpHeaders responseHeaders = new HttpHeaders();

                // Add rate limit headers for all requests except /health
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
}

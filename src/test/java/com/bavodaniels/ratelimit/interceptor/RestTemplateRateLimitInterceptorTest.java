package com.bavodaniels.ratelimit.interceptor;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RestTemplateRateLimitInterceptor.
 */
class RestTemplateRateLimitInterceptorTest {

    private RateLimitTracker tracker;
    private RestTemplateRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000); // 5 second threshold
    }

    @Test
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new RestTemplateRateLimitInterceptor(null));

        assertThrows(IllegalArgumentException.class, () ->
                new RestTemplateRateLimitInterceptor(tracker, -1));
    }

    @Test
    void testConstructorWithDefaultMaxWaitTime() {
        RestTemplateRateLimitInterceptor defaultInterceptor = new RestTemplateRateLimitInterceptor(tracker);
        assertEquals(30000, defaultInterceptor.getMaxWaitTimeMillis());
        assertEquals(tracker, defaultInterceptor.getRateLimitTracker());
    }

    @Test
    void testSuccessfulRequestWithNoRateLimitHeaders() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];
        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

        ClientHttpResponse response = interceptor.intercept(request, body, execution);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testRequestWithRateLimitHeaders() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");
        responseHeaders.set("X-RateLimit-Remaining", "50");
        responseHeaders.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        ClientHttpResponse response = interceptor.intercept(request, body, execution);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify state was updated
        RateLimitState state = tracker.getState("api.example.com");
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    @Test
    void testRateLimitExceededWithRetryAfter() throws IOException {
        String host = "api.example.com";

        // Set up a state that requires waiting
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(10).getEpochSecond()));
        headers.set("Retry-After", "2"); // 2 seconds

        tracker.updateFromHeaders(host, headers);

        HttpRequest request = createMockRequest("http://" + host + "/test");
        byte[] body = new byte[0];
        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

        // Should block for approximately 2 seconds
        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = interceptor.intercept(request, body, execution);
        long elapsedTime = System.currentTimeMillis() - startTime;

        assertNotNull(response);
        assertTrue(elapsedTime >= 1800, "Should have waited at least 1.8 seconds, but was " + elapsedTime + "ms");
        assertTrue(elapsedTime < 3000, "Should not have waited more than 3 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testRateLimitExceededThrowsException() {
        String host = "api.example.com";

        // Set up a state that requires waiting beyond threshold
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "10"); // 10 seconds - exceeds our 5 second threshold

        tracker.updateFromHeaders(host, headers);

        HttpRequest request = createMockRequest("http://" + host + "/test");
        byte[] body = new byte[0];
        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () ->
                interceptor.intercept(request, body, execution));

        assertEquals(host, exception.getHost());
        assertEquals(10000, exception.getWaitTimeMillis());
        assertEquals(5000, exception.getThresholdMillis());
    }

    @Test
    void testInterruptedWhileWaiting() {
        String host = "api.example.com";

        // Set up a state that requires waiting
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "5"); // 5 seconds
        tracker.updateFromHeaders(host, headers);

        HttpRequest request = createMockRequest("http://" + host + "/test");
        byte[] body = new byte[0];
        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

        // Interrupt the thread while it's waiting
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt(); // Pre-interrupt the thread
            assertThrows(RuntimeException.class, () ->
                    interceptor.intercept(request, body, execution));
            assertTrue(Thread.currentThread().isInterrupted());
        });

        testThread.start();
        try {
            testThread.join(2000);
        } catch (InterruptedException e) {
            fail("Test thread join was interrupted");
        }
    }

    @Test
    void testHostExtractionWithPort() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com:8080/test");
        byte[] body = new byte[0];

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");
        responseHeaders.set("X-RateLimit-Remaining", "99");

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        interceptor.intercept(request, body, execution);

        // Verify state was stored with port
        RateLimitState state = tracker.getState("api.example.com:8080");
        assertEquals(100, state.getLimit());
    }

    @Test
    void testHostExtractionWithoutPort() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");
        responseHeaders.set("X-RateLimit-Remaining", "99");

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        interceptor.intercept(request, body, execution);

        // Verify state was stored without port
        RateLimitState state = tracker.getState("api.example.com");
        assertEquals(100, state.getLimit());
    }

    @Test
    void testGetters() {
        assertEquals(tracker, interceptor.getRateLimitTracker());
        assertEquals(5000, interceptor.getMaxWaitTimeMillis());
    }

    @Test
    void testIOExceptionDuringExecution() {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];

        ClientHttpRequestExecution execution = (req, b) -> {
            throw new IOException("Network error");
        };

        assertThrows(IOException.class, () ->
                interceptor.intercept(request, body, execution));
    }

    @Test
    void testIOExceptionWithResponseCleanup() {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];

        ClientHttpRequestExecution execution = (req, b) -> {
            // Return a response but then throw IOException
            MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
            // Simulate error after response is created
            throw new IOException("Network error after response");
        };

        assertThrows(IOException.class, () ->
                interceptor.intercept(request, body, execution));
    }

    @Test
    void testExceptionInHandlePostResponse() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com/test");
        byte[] body = new byte[0];

        // Create a tracker that throws an exception
        RateLimitTracker faultyTracker = new RateLimitTracker() {
            @Override
            public RateLimitState getState(String host) {
                return new RateLimitState(host, "/test");
            }

            @Override
            public void updateFromHeaders(String host, HttpHeaders headers) {
                throw new RuntimeException("Failed to parse headers");
            }

            @Override
            public void clearState(String host) {}

            @Override
            public void clearAll() {}
        };

        RestTemplateRateLimitInterceptor faultyInterceptor = new RestTemplateRateLimitInterceptor(faultyTracker, 5000);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        // Should not throw exception, just log and continue
        ClientHttpResponse response = faultyInterceptor.intercept(request, body, execution);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testExtractEndpointWithQueryString() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com/test?param1=value1&param2=value2");
        byte[] body = new byte[0];

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        ClientHttpResponse response = interceptor.intercept(request, body, execution);
        assertNotNull(response);
    }

    @Test
    void testExtractEndpointWithEmptyPath() throws IOException {
        HttpRequest request = createMockRequest("http://api.example.com");
        byte[] body = new byte[0];

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-RateLimit-Limit", "100");

        ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

        ClientHttpResponse response = interceptor.intercept(request, body, execution);
        assertNotNull(response);
    }

    // Helper methods

    private HttpRequest createMockRequest(String url) {
        return new MockClientHttpRequest(HttpMethod.GET, URI.create(url));
    }

    private ClientHttpRequestExecution createMockExecution(HttpStatus status, HttpHeaders headers) {
        return (request, body) -> {
            MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], status);
            response.getHeaders().putAll(headers);
            return response;
        };
    }
}

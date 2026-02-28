package com.bavodaniels.ratelimit.interceptor;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for concurrent request handling in RestTemplateRateLimitInterceptor.
 */
class RestTemplateRateLimitInterceptorConcurrentTest {

    private RateLimitTracker tracker;
    private RestTemplateRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000);
    }

    @Test
    void testConcurrentRequestsWithoutRateLimit() throws InterruptedException, ExecutionException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ClientHttpResponse>> futures = new ArrayList<>();

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Future<ClientHttpResponse> future = executor.submit(() -> {
                HttpRequest request = createMockRequest("http://api.example.com/test");
                byte[] body = new byte[0];
                ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

                try {
                    ClientHttpResponse response = interceptor.intercept(request, body, execution);
                    successCount.incrementAndGet();
                    return response;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        for (Future<ClientHttpResponse> future : futures) {
            assertNotNull(future.get());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get());
    }

    @Test
    void testConcurrentRequestsWithRateLimitHeaders() throws InterruptedException, ExecutionException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<ClientHttpResponse>> futures = new ArrayList<>();

        AtomicInteger requestCounter = new AtomicInteger(100);

        for (int i = 0; i < threadCount; i++) {
            Future<ClientHttpResponse> future = executor.submit(() -> {
                HttpRequest request = createMockRequest("http://api.example.com/test");
                byte[] body = new byte[0];

                // Each response updates the rate limit
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.set("X-RateLimit-Limit", "100");
                responseHeaders.set("X-RateLimit-Remaining", String.valueOf(requestCounter.decrementAndGet()));
                responseHeaders.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));

                ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

                try {
                    return interceptor.intercept(request, body, execution);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for all to complete
        for (Future<ClientHttpResponse> future : futures) {
            assertNotNull(future.get());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentRequestsWithBlockingBehavior() throws InterruptedException {
        String host = "api.example.com";

        // Set initial state requiring a short wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "1"); // 1 second wait
        tracker.updateFromHeaders(host, headers);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    HttpRequest request = createMockRequest("http://" + host + "/test");
                    byte[] body = new byte[0];
                    ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, new HttpHeaders());

                    interceptor.intercept(request, body, execution);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads at once
        long startTime = System.currentTimeMillis();
        startLatch.countDown();

        // Wait for completion
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        long elapsedTime = System.currentTimeMillis() - startTime;

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        // All threads should complete
        assertEquals(threadCount, completedCount.get());

        // Should have taken at least 1 second due to blocking
        assertTrue(elapsedTime >= 900, "Should have waited at least 0.9 seconds, but was " + elapsedTime + "ms");
    }

    @Test
    void testMultipleHostsConcurrently() throws InterruptedException, ExecutionException {
        int hostsCount = 3;
        int requestsPerHost = 5;
        ExecutorService executor = Executors.newFixedThreadPool(hostsCount * requestsPerHost);
        List<Future<ClientHttpResponse>> futures = new ArrayList<>();

        for (int hostIndex = 0; hostIndex < hostsCount; hostIndex++) {
            String host = "api" + hostIndex + ".example.com";

            for (int reqIndex = 0; reqIndex < requestsPerHost; reqIndex++) {
                Future<ClientHttpResponse> future = executor.submit(() -> {
                    HttpRequest request = createMockRequest("http://" + host + "/test");
                    byte[] body = new byte[0];

                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.set("X-RateLimit-Limit", "100");
                    responseHeaders.set("X-RateLimit-Remaining", "50");

                    ClientHttpRequestExecution execution = createMockExecution(HttpStatus.OK, responseHeaders);

                    try {
                        return interceptor.intercept(request, body, execution);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }
        }

        // Wait for all to complete
        for (Future<ClientHttpResponse> future : futures) {
            assertNotNull(future.get());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify each host has its own state
        for (int i = 0; i < hostsCount; i++) {
            String host = "api" + i + ".example.com";
            assertNotNull(tracker.getState(host));
        }
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

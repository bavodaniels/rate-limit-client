package com.bavodaniels.ratelimit.interceptor;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent integration tests for RestClient with RestTemplateRateLimitInterceptor.
 * Tests thread-safety and concurrent request handling.
 */
class RestClientRateLimitInterceptorConcurrentTest {

    private RateLimitTracker tracker;
    private RestTemplateRateLimitInterceptor interceptor;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        interceptor = new RestTemplateRateLimitInterceptor(tracker, 5000);
        restClient = RestClient.builder()
                .requestFactory(createMockRequestFactory())
                .requestInterceptor(interceptor)
                .build();
    }

    @Test
    void testConcurrentRequestsWithoutRateLimits() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    String result = restClient.get()
                            .uri("http://api.example.com/test")
                            .retrieve()
                            .body(String.class);
                    if ("OK".equals(result)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Request failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            }));
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All requests should complete within 10 seconds");
        assertEquals(threadCount, successCount.get(), "All requests should succeed");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentRequestsWithRateLimits() throws InterruptedException {
        String host = "api.example.com";

        // Set up rate limit state
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "50");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()));
        tracker.updateFromHeaders(host, headers);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    restClient.get()
                            .uri("http://" + host + "/test")
                            .retrieve()
                            .body(String.class);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for some requests if rate limited
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All requests should complete within 10 seconds");
        assertTrue(successCount.get() > 0, "At least some requests should succeed");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentRequestsWithWait() throws InterruptedException {
        String host = "api.example.com";

        // Set up a state that requires a short wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "1"); // 1 second wait
        tracker.updateFromHeaders(host, headers);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    restClient.get()
                            .uri("http://" + host + "/test")
                            .retrieve()
                            .body(String.class);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected for some requests
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "All requests should complete");
        long elapsedTime = System.currentTimeMillis() - startTime;

        // At least one thread should have waited
        assertTrue(elapsedTime >= 900, "At least one request should have waited");
        assertTrue(successCount.get() > 0, "At least some requests should succeed");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentUpdatesFromMultipleHosts() throws InterruptedException {
        int hostCount = 5;
        int requestsPerHost = 3;
        ExecutorService executor = Executors.newFixedThreadPool(hostCount * requestsPerHost);
        CountDownLatch latch = new CountDownLatch(hostCount * requestsPerHost);

        for (int h = 0; h < hostCount; h++) {
            final String host = "api" + h + ".example.com";

            for (int r = 0; r < requestsPerHost; r++) {
                executor.submit(() -> {
                    try {
                        restClient.get()
                                .uri("http://" + host + "/test")
                                .retrieve()
                                .body(String.class);
                    } catch (Exception e) {
                        // Ignore exceptions in this test
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All requests should complete");

        // Verify each host has its own state
        for (int h = 0; h < hostCount; h++) {
            String host = "api" + h + ".example.com";
            RateLimitState state = tracker.getState(host);
            assertNotNull(state, "State should exist for " + host);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void testConcurrentBuilderCreation() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<RestClient> clients = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RestClient client = RestClient.builder()
                            .requestFactory(createMockRequestFactory())
                            .requestInterceptor(interceptor)
                            .build();
                    clients.add(client);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All clients should be created");
        assertEquals(threadCount, clients.size(), "All clients should be created successfully");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    // Helper method to create a mock request factory for testing
    private ClientHttpRequestFactory createMockRequestFactory() {
        return (uri, httpMethod) -> new org.springframework.mock.http.client.MockClientHttpRequest(httpMethod, uri) {
            @Override
            public ClientHttpResponse executeInternal() throws IOException {
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.set("X-RateLimit-Limit", "100");
                responseHeaders.set("X-RateLimit-Remaining", "99");

                MockClientHttpResponse response = new MockClientHttpResponse("OK".getBytes(), HttpStatus.OK);
                response.getHeaders().putAll(responseHeaders);
                return response;
            }
        };
    }
}

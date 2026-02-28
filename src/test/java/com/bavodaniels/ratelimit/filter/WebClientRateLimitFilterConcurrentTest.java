package com.bavodaniels.ratelimit.filter;

import com.bavodaniels.ratelimit.tracker.InMemoryRateLimitTracker;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent integration tests for WebClientRateLimitFilter.
 * Tests reactive behavior under concurrent load with multiple simultaneous requests.
 */
class WebClientRateLimitFilterConcurrentTest {

    private RateLimitTracker tracker;
    private WebClientRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        filter = new WebClientRateLimitFilter(tracker);
    }

    @Test
    void testConcurrentRequests_WithoutRateLimit_AllSucceed() {
        // Arrange
        String host = "api.example.com";
        int requestCount = 10;

        AtomicInteger executionCount = new AtomicInteger(0);

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "1000")
                .header("X-RateLimit-Remaining", "990")
                .build();

        ExchangeFunction mockExchange = r -> {
            executionCount.incrementAndGet();
            return Mono.just(mockResponse);
        };

        // Act - create multiple concurrent requests
        Flux<ClientResponse> requests = Flux.range(0, requestCount)
                .flatMap(i -> {
                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                            URI.create("http://" + host + "/test/" + i))
                            .build();
                    return filter.filter(request, mockExchange);
                });

        // Assert
        StepVerifier.create(requests)
                .expectNextCount(requestCount)
                .verifyComplete();

        assertEquals(requestCount, executionCount.get());
    }

    @Test
    void testConcurrentRequests_WithRateLimit_AllDelayedNonBlocking() {
        // Arrange
        String host = "api.example.com";
        int requestCount = 5;
        long delaySeconds = 1; // Use 1 second delay for clarity

        // Set initial rate limit
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", String.valueOf(delaySeconds));
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(delaySeconds).getEpochSecond()));
        tracker.updateFromHeaders(host, headers);

        AtomicInteger executionCount = new AtomicInteger(0);

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r -> {
            executionCount.incrementAndGet();
            return Mono.just(mockResponse);
        };

        // Act
        long startTime = System.currentTimeMillis();

        Flux<ClientResponse> requests = Flux.range(0, requestCount)
                .flatMap(i -> {
                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                            URI.create("http://" + host + "/test/" + i))
                            .build();
                    return filter.filter(request, mockExchange);
                });

        // Assert
        StepVerifier.create(requests)
                .expectNextCount(requestCount)
                .verifyComplete();

        long elapsed = System.currentTimeMillis() - startTime;
        long delayMillis = delaySeconds * 1000;

        // All requests should have been delayed
        assertTrue(elapsed >= delayMillis - 100, "Expected total time >= " + (delayMillis - 100) + "ms, but was " + elapsed + "ms");
        assertEquals(requestCount, executionCount.get());
    }

    @Test
    void testConcurrentRequests_DifferentHosts_IndependentHandling() {
        // Arrange
        int hostsCount = 3;
        int requestsPerHost = 3;

        // Set rate limit on host1 only
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "1");
        tracker.updateFromHeaders("host1.example.com", headers);

        AtomicInteger executionCount = new AtomicInteger(0);

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r -> {
            executionCount.incrementAndGet();
            return Mono.just(mockResponse);
        };

        // Act - create requests for multiple hosts
        Flux<ClientResponse> requests = Flux.range(1, hostsCount)
                .flatMap(hostNum ->
                        Flux.range(1, requestsPerHost)
                                .flatMap(reqNum -> {
                                    String host = "host" + hostNum + ".example.com";
                                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                                            URI.create("http://" + host + "/test/" + reqNum))
                                            .build();
                                    return filter.filter(request, mockExchange);
                                })
                );

        // Assert
        long startTime = System.currentTimeMillis();

        StepVerifier.create(requests)
                .expectNextCount(hostsCount * requestsPerHost)
                .verifyComplete();

        long elapsed = System.currentTimeMillis() - startTime;

        // Non-rate-limited hosts should complete quickly
        // Only host1 should be delayed by 1 second
        assertTrue(elapsed >= 900 && elapsed < 2000,
                "Expected time between 900-2000ms, but was " + elapsed + "ms");
        assertEquals(hostsCount * requestsPerHost, executionCount.get());
    }

    @Test
    void testReactiveStreams_BackpressureHandling() {
        // Arrange
        String host = "api.example.com";
        int requestCount = 100;

        AtomicInteger executionCount = new AtomicInteger(0);

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Remaining", "50")
                .build();

        ExchangeFunction mockExchange = r -> {
            executionCount.incrementAndGet();
            return Mono.just(mockResponse)
                    .delayElement(Duration.ofMillis(10)); // Simulate network delay
        };

        // Act - create many concurrent requests with limited concurrency
        Flux<ClientResponse> requests = Flux.range(0, requestCount)
                .flatMap(i -> {
                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                            URI.create("http://" + host + "/test/" + i))
                            .build();
                    return filter.filter(request, mockExchange);
                }, 10); // Limit concurrency to 10

        // Assert
        StepVerifier.create(requests)
                .expectNextCount(requestCount)
                .verifyComplete();

        assertEquals(requestCount, executionCount.get());
    }

    @Test
    void testReactiveError_PropagatesCorrectly() {
        // Arrange
        String host = "api.example.com";

        ExchangeFunction failingExchange = r -> Mono.error(new RuntimeException("Network error"));

        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                URI.create("http://" + host + "/test"))
                .build();

        // Act & Assert
        StepVerifier.create(filter.filter(request, failingExchange))
                .expectErrorMatches(e -> e instanceof RuntimeException && e.getMessage().equals("Network error"))
                .verify();
    }

    @Test
    void testSequentialRequests_WithDynamicRateLimitUpdates() {
        // Arrange
        String host = "api.example.com";
        AtomicInteger requestNumber = new AtomicInteger(0);

        ExchangeFunction dynamicExchange = r -> {
            int reqNum = requestNumber.incrementAndGet();
            HttpStatus status = HttpStatus.OK;
            String remaining = String.valueOf(Math.max(0, 5 - reqNum));

            return Mono.just(ClientResponse.create(status)
                    .header("X-RateLimit-Limit", "5")
                    .header("X-RateLimit-Remaining", remaining)
                    .header("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()))
                    .build());
        };

        // Act - make sequential requests
        Mono<Void> sequentialRequests = Flux.range(1, 5)
                .concatMap(i -> {
                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                            URI.create("http://" + host + "/test/" + i))
                            .build();
                    return filter.filter(request, dynamicExchange);
                })
                .then();

        // Assert
        StepVerifier.create(sequentialRequests)
                .verifyComplete();

        assertEquals(5, requestNumber.get());
    }

    @Test
    void testNonBlockingDelay_DoesNotBlockThreadPool() {
        // Arrange
        String host = "api.example.com";

        // Set up rate limit requiring 500ms wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "1");
        tracker.updateFromHeaders(host, headers);

        AtomicInteger executionCount = new AtomicInteger(0);

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r -> {
            executionCount.incrementAndGet();
            return Mono.just(mockResponse);
        };

        // Act - create many concurrent delayed requests
        // If blocking, this would exhaust thread pool
        Flux<ClientResponse> requests = Flux.range(0, 20)
                .flatMap(i -> {
                    ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                            URI.create("http://" + host + "/test/" + i))
                            .build();
                    return filter.filter(request, mockExchange);
                });

        // Assert - should complete without hanging
        StepVerifier.create(requests.timeout(Duration.ofSeconds(5)))
                .expectNextCount(20)
                .verifyComplete();

        assertEquals(20, executionCount.get());
    }

    @Test
    void testFilter_PreservesReactiveContext() {
        // Arrange
        String host = "api.example.com";
        String contextKey = "requestId";
        String contextValue = "test-123";

        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET,
                URI.create("http://" + host + "/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r ->
                Mono.deferContextual(ctx -> {
                    assertEquals(contextValue, ctx.get(contextKey));
                    return Mono.just(mockResponse);
                });

        // Act & Assert
        StepVerifier.create(filter.filter(request, mockExchange)
                        .contextWrite(ctx -> ctx.put(contextKey, contextValue)))
                .expectNext(mockResponse)
                .verifyComplete();
    }
}

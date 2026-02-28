package com.bavodaniels.ratelimit.filter;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebClientRateLimitFilter.
 * Tests reactive rate limiting behavior including non-blocking delays.
 */
class WebClientRateLimitFilterTest {

    private RateLimitTracker tracker;
    private WebClientRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
        filter = new WebClientRateLimitFilter(tracker);
    }

    @Test
    void testConstructor_WithNullTracker_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new WebClientRateLimitFilter(null));
    }

    @Test
    void testConstructor_WithNegativeMaxWaitTime_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new WebClientRateLimitFilter(tracker, -1));
    }

    @Test
    void testFilter_NoRateLimit_ExecutesImmediately() {
        // Arrange
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://api.example.com/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "100")
                .header("X-RateLimit-Remaining", "99")
                .header("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()))
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act & Assert
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        // Verify state was updated
        RateLimitState state = tracker.getState("api.example.com");
        assertEquals(100, state.getLimit());
        assertEquals(99, state.getRemaining());
    }

    @Test
    void testFilter_WithRateLimit_DelaysNonBlocking() {
        // Arrange
        String host = "api.example.com";
        long delayMillis = 200;

        // Set up initial rate limit state requiring wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(delayMillis / 1000 + 1).getEpochSecond()));
        headers.set("Retry-After", String.valueOf(delayMillis / 1000));
        tracker.updateFromHeaders(host, headers);

        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act & Assert - verify delay occurs
        long startTime = System.currentTimeMillis();

        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed >= delayMillis - 50, "Expected delay of at least " + (delayMillis - 50) + "ms, but was " + elapsed + "ms");
    }

    @Test
    void testFilter_WithExcessiveWaitTime_ThrowsException() {
        // Arrange
        String host = "api.example.com";
        WebClientRateLimitFilter shortThresholdFilter = new WebClientRateLimitFilter(tracker, 100);

        // Set up rate limit requiring 5 second wait
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Remaining", "0");
        headers.set("Retry-After", "5"); // 5 seconds
        tracker.updateFromHeaders(host, headers);

        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(ClientResponse.create(HttpStatus.OK).build());

        // Act & Assert
        StepVerifier.create(shortThresholdFilter.filter(request, mockExchange))
                .expectErrorMatches(e -> e instanceof RateLimitExceededException &&
                        ((RateLimitExceededException) e).getHost().equals(host))
                .verify();
    }

    @Test
    void testFilter_UpdatesRateLimitFromResponseHeaders() {
        // Arrange
        String host = "api.example.com";
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        long resetTime = Instant.now().plusSeconds(3600).getEpochSecond();
        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "1000")
                .header("X-RateLimit-Remaining", "500")
                .header("X-RateLimit-Reset", String.valueOf(resetTime))
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        // Assert
        RateLimitState state = tracker.getState(host);
        assertEquals(1000, state.getLimit());
        assertEquals(500, state.getRemaining());
        assertNotNull(state.getResetTime());
    }

    @Test
    void testFilter_HandlesPortInHost() {
        // Arrange
        String host = "api.example.com:8080";
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "100")
                .header("X-RateLimit-Remaining", "99")
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        // Assert - verify host includes port
        RateLimitState state = tracker.getState(host);
        assertEquals(100, state.getLimit());
    }

    @Test
    void testFilter_IgnoresStandardPorts() {
        // Arrange
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://api.example.com:80/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "100")
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        // Assert - host should not include port 80
        RateLimitState state = tracker.getState("api.example.com");
        assertEquals(100, state.getLimit());
    }

    @Test
    void testFilter_ContinuesOnHeaderParseError() {
        // Arrange
        String host = "api.example.com";
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        // Response with malformed headers
        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Limit", "invalid")
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act & Assert - should not fail
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();
    }

    @Test
    void testGetRateLimitTracker() {
        assertEquals(tracker, filter.getRateLimitTracker());
    }

    @Test
    void testGetMaxWaitTimeMillis() {
        assertEquals(30000, filter.getMaxWaitTimeMillis());

        WebClientRateLimitFilter customFilter = new WebClientRateLimitFilter(tracker, 5000);
        assertEquals(5000, customFilter.getMaxWaitTimeMillis());
    }

    @Test
    void testFilter_ReactiveChaining_PreservesNonBlockingBehavior() {
        // Arrange
        String host = "api.example.com";
        ClientRequest request = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host + "/test"))
                .build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("X-RateLimit-Remaining", "50")
                .build();

        ExchangeFunction mockExchange = r -> Mono.just(mockResponse).delayElement(Duration.ofMillis(50));

        // Act & Assert - verify the entire chain is reactive
        StepVerifier.create(filter.filter(request, mockExchange))
                .expectSubscription()
                .expectNext(mockResponse)
                .verifyComplete();
    }

    @Test
    void testFilter_MultipleRequests_IndependentDelays() {
        // Arrange
        String host1 = "api1.example.com";
        String host2 = "api2.example.com";

        // Set rate limit on host1
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("X-RateLimit-Remaining", "0");
        headers1.set("Retry-After", "1"); // 1 second
        tracker.updateFromHeaders(host1, headers1);

        ClientRequest request1 = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host1 + "/test")).build();
        ClientRequest request2 = ClientRequest.create(org.springframework.http.HttpMethod.GET, URI.create("http://" + host2 + "/test")).build();

        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK).build();
        ExchangeFunction mockExchange = r -> Mono.just(mockResponse);

        // Act & Assert - host2 should not be delayed
        long startTime = System.currentTimeMillis();

        StepVerifier.create(filter.filter(request2, mockExchange))
                .expectNext(mockResponse)
                .verifyComplete();

        long elapsed = System.currentTimeMillis() - startTime;
        assertTrue(elapsed < 500, "Host2 should not be delayed, but took " + elapsed + "ms");
    }
}

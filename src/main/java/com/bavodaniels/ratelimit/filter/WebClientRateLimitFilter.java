package com.bavodaniels.ratelimit.filter;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

/**
 * WebClient filter that automatically handles rate limiting for reactive HTTP requests.
 * This filter checks rate limit state before each request and uses non-blocking delays
 * with Mono.delay() if necessary. After receiving a response, it updates the rate limit
 * state based on response headers in a reactive manner.
 *
 * <p>Thread-safe and fully reactive for use with concurrent requests.</p>
 *
 * @since 1.0.0
 */
public class WebClientRateLimitFilter implements ExchangeFilterFunction {

    private final RateLimitTracker rateLimitTracker;
    private final long maxWaitTimeMillis;

    /**
     * Creates a new WebClientRateLimitFilter with default max wait time of 30 seconds.
     *
     * @param rateLimitTracker the tracker to use for managing rate limit state
     */
    public WebClientRateLimitFilter(RateLimitTracker rateLimitTracker) {
        this(rateLimitTracker, 30000); // Default 30 seconds
    }

    /**
     * Creates a new WebClientRateLimitFilter with a custom max wait time.
     *
     * @param rateLimitTracker the tracker to use for managing rate limit state
     * @param maxWaitTimeMillis the maximum time in milliseconds to wait before throwing an exception
     */
    public WebClientRateLimitFilter(RateLimitTracker rateLimitTracker, long maxWaitTimeMillis) {
        if (rateLimitTracker == null) {
            throw new IllegalArgumentException("RateLimitTracker cannot be null");
        }
        if (maxWaitTimeMillis < 0) {
            throw new IllegalArgumentException("Max wait time cannot be negative");
        }
        this.rateLimitTracker = rateLimitTracker;
        this.maxWaitTimeMillis = maxWaitTimeMillis;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String host = extractHost(request.url());
        String endpoint = extractEndpoint(request.url());

        // Pre-request: Check rate limit state and handle non-blocking delay
        return handlePreRequest(host, endpoint)
                .then(Mono.defer(() -> next.exchange(request)))
                .flatMap(response -> handlePostResponse(host, response));
    }

    /**
     * Handles pre-request rate limit checking with reactive delays.
     * Checks the current rate limit state and returns a Mono that delays if necessary.
     *
     * @param host the host to check
     * @param endpoint the endpoint being accessed
     * @return a Mono that completes after any required delay, or emits an error if threshold exceeded
     */
    private Mono<Void> handlePreRequest(String host, String endpoint) {
        return Mono.fromCallable(() -> rateLimitTracker.getState(host))
                .flatMap(state -> {
                    if (state.requiresWait()) {
                        long waitTime = state.getRetryAfterMillis();

                        // Check if wait time exceeds threshold
                        if (waitTime > maxWaitTimeMillis) {
                            java.time.Instant retryAfter = state.getResetTime();
                            java.time.Duration waitDuration = Duration.ofMillis(waitTime);
                            return Mono.error(new RateLimitExceededException(host, endpoint, retryAfter, waitDuration));
                        }

                        // Non-blocking delay using Mono.delay()
                        if (waitTime > 0) {
                            return Mono.delay(Duration.ofMillis(waitTime)).then();
                        }
                    }
                    return Mono.empty();
                });
    }

    /**
     * Handles post-response processing in a reactive manner.
     * Updates the rate limit state based on response headers and returns the response.
     *
     * @param host the host to update
     * @param response the HTTP response containing rate limit headers
     * @return a Mono containing the response
     */
    private Mono<ClientResponse> handlePostResponse(String host, ClientResponse response) {
        return Mono.fromRunnable(() -> {
            try {
                rateLimitTracker.updateFromHeaders(host, response.headers().asHttpHeaders());
            } catch (Exception e) {
                // Log error but don't fail the request
                // In production, this would use a logger
                System.err.println("Failed to update rate limit state for host " + host + ": " + e.getMessage());
            }
        }).thenReturn(response);
    }

    /**
     * Extracts the host from a URI.
     *
     * @param uri the URI to extract from
     * @return the host (hostname:port or just hostname)
     */
    private String extractHost(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();

        if (port != -1 && port != 80 && port != 443) {
            return host + ":" + port;
        }

        return host;
    }

    /**
     * Extracts the endpoint (path + query) from a URI.
     *
     * @param uri the URI to extract from
     * @return the endpoint path and query string
     */
    private String extractEndpoint(URI uri) {
        String path = uri.getPath();
        String query = uri.getQuery();

        if (query != null && !query.isEmpty()) {
            return path + "?" + query;
        }

        return path != null && !path.isEmpty() ? path : "/";
    }

    /**
     * Gets the rate limit tracker used by this filter.
     *
     * @return the rate limit tracker
     */
    public RateLimitTracker getRateLimitTracker() {
        return rateLimitTracker;
    }

    /**
     * Gets the maximum wait time in milliseconds.
     *
     * @return the max wait time
     */
    public long getMaxWaitTimeMillis() {
        return maxWaitTimeMillis;
    }
}

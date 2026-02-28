package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitState;
import org.springframework.http.HttpHeaders;

/**
 * Interface for tracking rate limit state across requests.
 * Implementations must be thread-safe to handle concurrent requests.
 *
 * @since 1.0.0
 */
public interface RateLimitTracker {

    /**
     * Gets the current rate limit state for the specified host.
     * If no state exists, returns a state indicating no limits.
     *
     * @param host the host to check
     * @return the current rate limit state, never null
     */
    RateLimitState getState(String host);

    /**
     * Updates the rate limit state based on response headers.
     * Parses headers like X-RateLimit-Limit, X-RateLimit-Remaining,
     * X-RateLimit-Reset, Retry-After, etc.
     *
     * @param host the host to update
     * @param headers the response headers containing rate limit information
     */
    void updateFromHeaders(String host, HttpHeaders headers);

    /**
     * Clears the rate limit state for the specified host.
     *
     * @param host the host to clear
     */
    void clearState(String host);

    /**
     * Clears all rate limit states.
     */
    void clearAll();
}

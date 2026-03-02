package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitState;
import org.springframework.http.HttpHeaders;

/**
 * Interface for tracking rate limit state across requests.
 * Implementations must be thread-safe to handle concurrent requests.
 *
 * <p>Supports both single-bucket and multi-bucket rate limiting. When response headers
 * contain multiple rate limit buckets (e.g., daily, hourly, per-resource limits),
 * all buckets are tracked simultaneously. A request is allowed only if ALL buckets
 * have capacity.</p>
 *
 * @since 1.0.0
 */
public interface RateLimitTracker {

    /**
     * Gets the current rate limit state for the specified host.
     * If no state exists, returns a state indicating no limits.
     *
     * <p>For multi-bucket APIs, the returned state contains information about all
     * tracked buckets and their limits.</p>
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
     * <p>Also automatically detects and parses multi-bucket headers with pattern
     * {@code X-RateLimit-{BucketName}-Limit}, {@code X-RateLimit-{BucketName}-Remaining},
     * {@code X-RateLimit-{BucketName}-Reset}.</p>
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

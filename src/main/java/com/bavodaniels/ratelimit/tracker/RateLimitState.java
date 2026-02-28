package com.bavodaniels.ratelimit.tracker;

import java.time.Instant;

/**
 * Immutable class representing the rate limit state for a specific host/endpoint.
 * Thread-safe due to immutability.
 *
 * @since 1.0.0
 */
public record RateLimitState(
    int limit,
    int remaining,
    Instant resetTime,
    Instant lastUpdated
) {

    /**
     * Creates a new RateLimitState with the current timestamp.
     *
     * @param limit the rate limit (max requests allowed)
     * @param remaining the number of requests remaining
     * @param resetTime when the rate limit will reset
     * @return a new RateLimitState instance
     */
    public static RateLimitState of(int limit, int remaining, Instant resetTime) {
        return new RateLimitState(limit, remaining, resetTime, Instant.now());
    }

    /**
     * Checks if this rate limit state has expired (reset time has passed).
     *
     * @return true if the rate limit has reset, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(resetTime);
    }

    /**
     * Checks if requests are currently being rate limited (remaining is 0).
     *
     * @return true if no requests remain, false otherwise
     */
    public boolean isLimited() {
        return remaining <= 0;
    }

    /**
     * Calculates the time in milliseconds until the rate limit resets.
     *
     * @return milliseconds until reset, or 0 if already reset
     */
    public long getWaitTimeMillis() {
        if (isExpired()) {
            return 0;
        }
        return resetTime.toEpochMilli() - Instant.now().toEpochMilli();
    }
}

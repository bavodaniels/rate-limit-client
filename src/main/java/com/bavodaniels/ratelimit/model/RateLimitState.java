package com.bavodaniels.ratelimit.model;

import java.time.Instant;

/**
 * Represents the current rate limit state for a host or endpoint.
 *
 * @since 1.0.0
 */
public class RateLimitState {

    private final int limit;
    private final int remaining;
    private final Instant resetTime;
    private final long retryAfterMillis;

    /**
     * Creates a new RateLimitState.
     *
     * @param limit the maximum number of requests allowed in the time window
     * @param remaining the number of requests remaining in the current time window
     * @param resetTime the time when the rate limit resets
     * @param retryAfterMillis the number of milliseconds to wait before retrying (0 if no wait needed)
     */
    public RateLimitState(int limit, int remaining, Instant resetTime, long retryAfterMillis) {
        this.limit = limit;
        this.remaining = remaining;
        this.resetTime = resetTime;
        this.retryAfterMillis = retryAfterMillis;
    }

    /**
     * Gets the maximum number of requests allowed in the time window.
     *
     * @return the limit
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Gets the number of requests remaining in the current time window.
     *
     * @return the remaining count
     */
    public int getRemaining() {
        return remaining;
    }

    /**
     * Gets the time when the rate limit resets.
     *
     * @return the reset time
     */
    public Instant getResetTime() {
        return resetTime;
    }

    /**
     * Gets the number of milliseconds to wait before retrying.
     *
     * @return the retry delay in milliseconds (0 if no wait needed)
     */
    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    /**
     * Checks if a wait is required before making the next request.
     *
     * @return true if retry after is greater than 0
     */
    public boolean requiresWait() {
        return retryAfterMillis > 0;
    }

    @Override
    public String toString() {
        return "RateLimitState{" +
                "limit=" + limit +
                ", remaining=" + remaining +
                ", resetTime=" + resetTime +
                ", retryAfterMillis=" + retryAfterMillis +
                '}';
    }
}

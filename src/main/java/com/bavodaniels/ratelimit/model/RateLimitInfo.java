package com.bavodaniels.ratelimit.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing rate limit information extracted from HTTP response headers.
 * This record captures the current state of rate limiting for a specific API endpoint.
 *
 * <p>Thread-safe due to immutability (Java record).</p>
 *
 * <p>Common HTTP headers this information is derived from:
 * <ul>
 *   <li>X-RateLimit-Limit / RateLimit-Limit - Maximum number of requests allowed</li>
 *   <li>X-RateLimit-Remaining / RateLimit-Remaining - Number of requests remaining</li>
 *   <li>X-RateLimit-Reset / RateLimit-Reset - Time when the rate limit resets</li>
 *   <li>Retry-After - Seconds to wait before retrying</li>
 * </ul>
 *
 * @param limit The maximum number of requests allowed in the current time window.
 *              Must be non-negative. A value of 0 indicates no rate limit information available.
 * @param remaining The number of requests remaining in the current time window.
 *                  Must be non-negative and not exceed limit.
 * @param resetTime The instant when the rate limit counter resets.
 *                  Must not be null. Use {@link Instant#EPOCH} if unknown.
 * @param retryAfter The duration in seconds to wait before making another request.
 *                   Must be non-negative. A value of 0 indicates immediate retry is possible.
 *
 * @since 1.0.0
 */
public record RateLimitInfo(
    long limit,
    long remaining,
    Instant resetTime,
    long retryAfter
) {

    /**
     * Canonical constructor with validation.
     *
     * @param limit The maximum number of requests allowed
     * @param remaining The number of requests remaining
     * @param resetTime The instant when the rate limit resets
     * @param retryAfter The duration in seconds to wait before retrying
     * @throws IllegalArgumentException if any validation fails
     */
    public RateLimitInfo {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative, got: " + limit);
        }
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must be non-negative, got: " + remaining);
        }
        if (remaining > limit) {
            throw new IllegalArgumentException(
                "remaining (" + remaining + ") cannot exceed limit (" + limit + ")"
            );
        }
        Objects.requireNonNull(resetTime, "resetTime must not be null");
        if (retryAfter < 0) {
            throw new IllegalArgumentException("retryAfter must be non-negative, got: " + retryAfter);
        }
    }

    /**
     * Creates a new RateLimitInfo indicating no rate limit information is available.
     *
     * @return A RateLimitInfo with all values set to defaults (0 for numerics, EPOCH for time)
     */
    public static RateLimitInfo empty() {
        return new RateLimitInfo(0, 0, Instant.EPOCH, 0);
    }

    /**
     * Checks if this rate limit information indicates the limit has been exceeded.
     *
     * @return true if remaining is 0 and limit is greater than 0, false otherwise
     */
    public boolean isLimitExceeded() {
        return limit > 0 && remaining == 0;
    }

    /**
     * Checks if this rate limit information has valid data (non-zero limit).
     *
     * @return true if limit is greater than 0, false otherwise
     */
    public boolean hasValidData() {
        return limit > 0;
    }

    /**
     * Calculates the time remaining until the rate limit resets.
     *
     * @param now The current instant
     * @return Duration in seconds until reset. Returns 0 if reset time has passed.
     * @throws NullPointerException if now is null
     */
    public long secondsUntilReset(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        long seconds = resetTime.getEpochSecond() - now.getEpochSecond();
        return Math.max(0, seconds);
    }

    /**
     * Creates a new RateLimitInfo with the remaining count decremented by one.
     * If remaining is already 0, returns this instance unchanged.
     *
     * @return A new RateLimitInfo with remaining decremented, or this instance if remaining is 0
     */
    public RateLimitInfo decrementRemaining() {
        if (remaining == 0) {
            return this;
        }
        return new RateLimitInfo(limit, remaining - 1, resetTime, retryAfter);
    }

    /**
     * Creates a builder for constructing RateLimitInfo instances.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing RateLimitInfo instances with optional parameters.
     */
    public static class Builder {
        private long limit = 0;
        private long remaining = 0;
        private Instant resetTime = Instant.EPOCH;
        private long retryAfter = 0;

        private Builder() {
        }

        /**
         * Sets the maximum number of requests allowed.
         *
         * @param limit The limit value
         * @return This builder
         */
        public Builder limit(long limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Sets the number of requests remaining.
         *
         * @param remaining The remaining value
         * @return This builder
         */
        public Builder remaining(long remaining) {
            this.remaining = remaining;
            return this;
        }

        /**
         * Sets the instant when the rate limit resets.
         *
         * @param resetTime The reset time
         * @return This builder
         */
        public Builder resetTime(Instant resetTime) {
            this.resetTime = resetTime;
            return this;
        }

        /**
         * Sets the duration to wait before retrying.
         *
         * @param retryAfter The retry after duration in seconds
         * @return This builder
         */
        public Builder retryAfter(long retryAfter) {
            this.retryAfter = retryAfter;
            return this;
        }

        /**
         * Builds the RateLimitInfo instance.
         *
         * @return A new RateLimitInfo
         * @throws IllegalArgumentException if validation fails
         */
        public RateLimitInfo build() {
            return new RateLimitInfo(limit, remaining, resetTime, retryAfter);
        }
    }
}

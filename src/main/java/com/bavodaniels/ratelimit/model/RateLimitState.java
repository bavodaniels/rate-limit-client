package com.bavodaniels.ratelimit.model;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe mutable state holder for tracking rate limit information for a specific host and endpoint.
 * Manages the current rate limit status and provides methods to query and update the state.
 *
 * <p>This class is thread-safe through the use of a ReadWriteLock.</p>
 *
 * @since 1.0.0
 */
public class RateLimitState {

    private final String host;
    private final String endpoint;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile RateLimitInfo currentInfo;
    private volatile Instant lastUpdated;

    /**
     * Creates a new RateLimitState for the given host and endpoint.
     *
     * @param host the host (e.g., "api.example.com"), must not be null or blank
     * @param endpoint the endpoint path (e.g., "/users"), must not be null or blank
     * @throws IllegalArgumentException if host or endpoint is null or blank
     */
    public RateLimitState(String host, String endpoint) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be null or blank");
        }
        this.host = host;
        this.endpoint = endpoint;
        this.currentInfo = RateLimitInfo.empty();
        this.lastUpdated = Instant.EPOCH;
    }

    /**
     * Gets the host for this state.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the endpoint for this state.
     *
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the current rate limit information.
     *
     * @return the current RateLimitInfo
     */
    public RateLimitInfo getCurrentInfo() {
        lock.readLock().lock();
        try {
            return currentInfo;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the time when the rate limit information was last updated.
     *
     * @return the last updated time
     */
    public Instant getLastUpdated() {
        lock.readLock().lock();
        try {
            return lastUpdated;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Updates the rate limit information with the current time.
     *
     * @param info the new rate limit information, must not be null
     * @throws NullPointerException if info is null
     */
    public void updateRateLimitInfo(RateLimitInfo info) {
        updateRateLimitInfo(info, Instant.now());
    }

    /**
     * Updates the rate limit information with a specific timestamp.
     *
     * @param info the new rate limit information, must not be null
     * @param updateTime the time of the update, must not be null
     * @throws NullPointerException if info or updateTime is null
     */
    public void updateRateLimitInfo(RateLimitInfo info, Instant updateTime) {
        Objects.requireNonNull(info, "info must not be null");
        Objects.requireNonNull(updateTime, "updateTime must not be null");

        lock.writeLock().lock();
        try {
            this.currentInfo = info;
            this.lastUpdated = updateTime;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the maximum number of requests allowed.
     * Convenience method that delegates to getCurrentInfo().limit().
     *
     * @return the limit
     */
    public long getLimit() {
        return getCurrentInfo().limit();
    }

    /**
     * Gets the number of requests remaining.
     * Convenience method that delegates to getCurrentInfo().remaining().
     *
     * @return the remaining count
     */
    public long getRemaining() {
        return getCurrentInfo().remaining();
    }

    /**
     * Gets the time when the rate limit resets.
     * Convenience method that delegates to getCurrentInfo().resetTime().
     *
     * @return the reset time
     */
    public Instant getResetTime() {
        return getCurrentInfo().resetTime();
    }

    /**
     * Gets the retry after duration in seconds.
     * Convenience method that delegates to getCurrentInfo().retryAfter().
     *
     * @return the retry after in seconds
     */
    public long getRetryAfter() {
        return getCurrentInfo().retryAfter();
    }

    /**
     * Checks if the rate limit has been exceeded.
     *
     * @return true if the limit is exceeded, false otherwise
     */
    public boolean isLimitExceeded() {
        return getCurrentInfo().isLimitExceeded();
    }

    /**
     * Checks if the rate limit data is stale (reset time has passed or no valid data).
     *
     * @param now the current time
     * @return true if the data is stale, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean isStale(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        RateLimitInfo info = getCurrentInfo();

        if (!info.hasValidData()) {
            return true;
        }

        return !now.isBefore(info.resetTime());
    }

    /**
     * Checks if a request can be made at the given time.
     *
     * @param now the current time
     * @return true if a request can be made, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean canMakeRequest(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        // If data is stale or no valid data, allow request
        if (isStale(now)) {
            return true;
        }

        // If limit is not exceeded, allow request
        return !isLimitExceeded();
    }

    /**
     * Gets the wait time in seconds before the next request can be made.
     *
     * @param now the current time
     * @return the wait time in seconds, or 0 if no wait is needed
     * @throws NullPointerException if now is null
     */
    public long getWaitTimeSeconds(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        if (canMakeRequest(now)) {
            return 0;
        }

        RateLimitInfo info = getCurrentInfo();

        // Use retryAfter if available
        if (info.retryAfter() > 0) {
            return info.retryAfter();
        }

        // Otherwise, use time until reset
        return info.secondsUntilReset(now);
    }

    /**
     * Resets the state to empty.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            this.currentInfo = RateLimitInfo.empty();
            this.lastUpdated = Instant.EPOCH;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates an immutable snapshot of the current state.
     *
     * @return a snapshot of the current state
     */
    public StateSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new StateSnapshot(host, endpoint, currentInfo, lastUpdated);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitState that = (RateLimitState) o;
        return Objects.equals(host, that.host) && Objects.equals(endpoint, that.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, endpoint);
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return "RateLimitState{" +
                    "host='" + host + '\'' +
                    ", endpoint='" + endpoint + '\'' +
                    ", currentInfo=" + currentInfo +
                    ", lastUpdated=" + lastUpdated +
                    '}';
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Immutable snapshot of a RateLimitState.
     *
     * @param host the host
     * @param endpoint the endpoint
     * @param info the rate limit information
     * @param lastUpdated the last updated time
     */
    public record StateSnapshot(
            String host,
            String endpoint,
            RateLimitInfo info,
            Instant lastUpdated
    ) {
        public StateSnapshot {
            Objects.requireNonNull(host, "host must not be null");
            Objects.requireNonNull(endpoint, "endpoint must not be null");
            Objects.requireNonNull(info, "info must not be null");
            Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
        }
    }
}

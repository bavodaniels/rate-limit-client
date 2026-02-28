package com.bavodaniels.ratelimit.model;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe class for tracking rate limit state for a specific host and endpoint combination.
 * This class maintains the current rate limit information and provides thread-safe access
 * and update operations.
 *
 * <p>Thread-safety is achieved through:
 * <ul>
 *   <li>Read-write locks for concurrent access</li>
 *   <li>Immutable RateLimitInfo instances</li>
 *   <li>Defensive copying</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RateLimitState state = new RateLimitState("api.example.com", "/users");
 * RateLimitInfo info = RateLimitInfo.builder()
 *     .limit(100)
 *     .remaining(50)
 *     .resetTime(Instant.now().plusSeconds(3600))
 *     .build();
 * state.updateRateLimitInfo(info);
 * }</pre>
 *
 * @since 1.0.0
 */
public class RateLimitState {

    private final String host;
    private final String endpoint;
    private final ReadWriteLock lock;
    private volatile RateLimitInfo currentInfo;
    private volatile Instant lastUpdated;

    /**
     * Creates a new RateLimitState for the specified host and endpoint.
     *
     * @param host The host (e.g., "api.example.com"). Must not be null or blank.
     * @param endpoint The endpoint path (e.g., "/users"). Must not be null or blank.
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
        this.lock = new ReentrantReadWriteLock();
        this.currentInfo = RateLimitInfo.empty();
        this.lastUpdated = Instant.EPOCH;
    }

    /**
     * Gets the host associated with this rate limit state.
     *
     * @return The host string
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the endpoint associated with this rate limit state.
     *
     * @return The endpoint string
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the current rate limit information.
     * This operation is thread-safe.
     *
     * @return The current RateLimitInfo (never null)
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
     * Gets the instant when the rate limit information was last updated.
     * This operation is thread-safe.
     *
     * @return The last updated instant (never null)
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
     * Updates the rate limit information.
     * This operation is thread-safe.
     *
     * @param info The new rate limit information. Must not be null.
     * @throws NullPointerException if info is null
     */
    public void updateRateLimitInfo(RateLimitInfo info) {
        Objects.requireNonNull(info, "info must not be null");
        lock.writeLock().lock();
        try {
            this.currentInfo = info;
            this.lastUpdated = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates the rate limit information with a timestamp.
     * This operation is thread-safe.
     *
     * @param info The new rate limit information. Must not be null.
     * @param updateTime The update timestamp. Must not be null.
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
     * Checks if the rate limit is currently exceeded.
     * This operation is thread-safe.
     *
     * @return true if the limit is exceeded, false otherwise
     */
    public boolean isLimitExceeded() {
        lock.readLock().lock();
        try {
            return currentInfo.isLimitExceeded();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if the rate limit information should be considered stale.
     * Information is considered stale if the reset time has passed.
     * This operation is thread-safe.
     *
     * @param now The current instant. Must not be null.
     * @return true if the rate limit info is stale, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean isStale(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        lock.readLock().lock();
        try {
            // If we have no valid data, it's stale
            if (!currentInfo.hasValidData()) {
                return true;
            }
            // If the reset time has passed, it's stale
            return now.isAfter(currentInfo.resetTime());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a request can be made based on the current rate limit state.
     * This operation is thread-safe.
     *
     * @param now The current instant. Must not be null.
     * @return true if a request can be made, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean canMakeRequest(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        lock.readLock().lock();
        try {
            // If data is stale, we can try
            if (isStale(now)) {
                return true;
            }
            // If no valid data, we can try
            if (!currentInfo.hasValidData()) {
                return true;
            }
            // Check if we have remaining requests
            return currentInfo.remaining() > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Calculates the recommended wait time in seconds before making the next request.
     * This operation is thread-safe.
     *
     * @param now The current instant. Must not be null.
     * @return Wait time in seconds. Returns 0 if no wait is needed.
     * @throws NullPointerException if now is null
     */
    public long getWaitTimeSeconds(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        lock.readLock().lock();
        try {
            // If we can make a request, no wait needed
            if (canMakeRequest(now)) {
                return 0;
            }
            // Use retryAfter if available, otherwise time until reset
            if (currentInfo.retryAfter() > 0) {
                return currentInfo.retryAfter();
            }
            return currentInfo.secondsUntilReset(now);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Resets the rate limit state to empty.
     * This operation is thread-safe.
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
     * Creates a snapshot of the current state for debugging or logging purposes.
     * This operation is thread-safe.
     *
     * @return A StateSnapshot containing the current state
     */
    public StateSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return new StateSnapshot(host, endpoint, currentInfo, lastUpdated);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Immutable snapshot of rate limit state at a point in time.
     *
     * @param host The host
     * @param endpoint The endpoint
     * @param info The rate limit information
     * @param lastUpdated The last update time
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitState that = (RateLimitState) o;
        return Objects.equals(host, that.host) &&
               Objects.equals(endpoint, that.endpoint);
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
}

package com.bavodaniels.ratelimit.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe mutable state holder for tracking rate limit information for a specific host and endpoint.
 * Manages the current rate limit status and provides methods to query and update the state.
 *
 * <p>Supports multiple rate limit buckets (e.g., daily limit, hourly limit, per-resource limit)
 * as well as single-bucket configurations for backwards compatibility.</p>
 *
 * <h2>Multi-Bucket Rate Limiting Behavior</h2>
 * <p>When multiple buckets are tracked:
 * <ul>
 *   <li>{@link #canMakeRequest(Instant)} returns true only if ALL buckets allow the request</li>
 *   <li>{@link #isLimitExceeded()} returns true if ANY bucket is exceeded</li>
 *   <li>{@link #getWaitTimeSeconds(Instant)} returns the MAXIMUM wait time across all buckets</li>
 *   <li>For backwards compatibility, {@link #getCurrentInfo()} returns the most restrictive bucket</li>
 * </ul>
 * </p>
 *
 * <p>This class is thread-safe through the use of a ReadWriteLock.</p>
 *
 * <p>Example usage with multi-bucket:
 * <pre>
 * RateLimitState state = tracker.getState("api.example.com");
 * if (state.isLimitExceeded()) {
 *     long waitSeconds = state.getWaitTimeSeconds(Instant.now());
 *     Thread.sleep(waitSeconds * 1000);
 * }
 * RateLimitInfo orders = state.getBucketInfo("SessionOrders");
 * System.out.println("Orders remaining: " + orders.remaining());
 * </pre>
 * </p>
 *
 * @since 1.0.0
 */
public class RateLimitState {

    private final String host;
    private final String endpoint;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Map<String, RateLimitInfo> buckets;
    private Instant lastUpdated;

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
        this.buckets = new HashMap<>(4); // Typical bucket counts are small (1-5)
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
     * For backwards compatibility, returns the most restrictive bucket if multiple buckets exist.
     * Returns empty RateLimitInfo if no buckets are configured.
     *
     * @return the current RateLimitInfo (most restrictive bucket)
     */
    public RateLimitInfo getCurrentInfo() {
        Instant now = Instant.now();
        lock.readLock().lock();
        try {
            return getMostRestrictiveInfo(now);
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
     * For backwards compatibility, stores the info in a "default" bucket.
     *
     * @param info the new rate limit information, must not be null
     * @throws NullPointerException if info is null
     */
    public void updateRateLimitInfo(RateLimitInfo info) {
        updateRateLimitInfo(info, Instant.now());
    }

    /**
     * Updates the rate limit information with a specific timestamp.
     * For backwards compatibility, stores the info in a "default" bucket.
     *
     * @param info the new rate limit information, must not be null
     * @param updateTime the time of the update, must not be null
     * @throws NullPointerException if info or updateTime is null
     */
    public void updateRateLimitInfo(RateLimitInfo info, Instant updateTime) {
        Objects.requireNonNull(info, "info must not be null");
        Objects.requireNonNull(updateTime, "updateTime must not be null");

        updateRateLimitInfo(Map.of("default", info), updateTime);
    }

    /**
     * Updates the rate limit information for multiple buckets with the current time.
     *
     * @param buckets map of bucket names to rate limit information, must not be null
     * @throws NullPointerException if buckets is null
     * @since 1.1.0
     */
    public void updateRateLimitInfo(Map<String, RateLimitInfo> buckets) {
        updateRateLimitInfo(buckets, Instant.now());
    }

    /**
     * Updates the rate limit information for multiple buckets with a specific timestamp.
     *
     * @param buckets map of bucket names to rate limit information, must not be null
     * @param updateTime the time of the update, must not be null
     * @throws NullPointerException if buckets or updateTime is null
     * @since 1.1.0
     */
    public void updateRateLimitInfo(Map<String, RateLimitInfo> buckets, Instant updateTime) {
        Objects.requireNonNull(buckets, "buckets must not be null");
        Objects.requireNonNull(updateTime, "updateTime must not be null");

        lock.writeLock().lock();
        try {
            // Store direct reference; callers create new maps per update, so no defensive copy needed
            this.buckets = buckets;
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
     * Returns true if ANY bucket has exceeded its limit.
     *
     * @return true if any bucket's limit is exceeded, false otherwise
     */
    public boolean isLimitExceeded() {
        lock.readLock().lock();
        try {
            if (buckets.isEmpty()) {
                return false;
            }
            return buckets.values().stream()
                .anyMatch(RateLimitInfo::isLimitExceeded);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if the rate limit data is stale (reset time has passed or no valid data).
     * Returns true if ALL buckets are stale or no buckets exist.
     *
     * @param now the current time
     * @return true if all buckets are stale or no buckets exist, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean isStale(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        lock.readLock().lock();
        try {
            if (buckets.isEmpty()) {
                return true;
            }

            // All buckets must be stale for the state to be considered stale
            for (RateLimitInfo info : buckets.values()) {
                if (!info.hasValidData()) {
                    continue; // Invalid data is stale, check next bucket
                }
                if (now.isBefore(info.resetTime())) {
                    return false; // At least one bucket is not stale
                }
            }
            return true; // All buckets are stale
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a request can be made at the given time.
     * Returns true only if ALL buckets allow the request.
     *
     * @param now the current time
     * @return true if all buckets allow the request, false otherwise
     * @throws NullPointerException if now is null
     */
    public boolean canMakeRequest(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        lock.readLock().lock();
        try {
            // If data is stale or no valid data, allow request
            if (buckets.isEmpty()) {
                return true;
            }

            // All buckets must allow the request
            for (RateLimitInfo info : buckets.values()) {
                // Skip invalid buckets
                if (!info.hasValidData()) {
                    continue;
                }

                // Check if bucket is stale (reset time has passed)
                if (!now.isBefore(info.resetTime())) {
                    continue; // Stale bucket, allow request
                }

                // If bucket limit is exceeded and not stale, deny request
                if (info.isLimitExceeded()) {
                    return false;
                }
            }

            return true; // All buckets allow the request
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the wait time in seconds before the next request can be made.
     * Returns the MAXIMUM wait time across all buckets.
     *
     * @param now the current time
     * @return the wait time in seconds, or 0 if no wait is needed
     * @throws NullPointerException if now is null
     */
    public long getWaitTimeSeconds(Instant now) {
        Objects.requireNonNull(now, "now must not be null");

        lock.readLock().lock();
        try {
            long maxWaitTime = 0;
            for (RateLimitInfo info : buckets.values()) {
                if (!info.hasValidData() || !now.isBefore(info.resetTime()) || !info.isLimitExceeded()) {
                    continue;
                }
                long waitTime = info.retryAfter() > 0 ? info.retryAfter() : info.secondsUntilReset(now);
                maxWaitTime = Math.max(maxWaitTime, waitTime);
            }
            return maxWaitTime;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets rate limit information for a specific bucket.
     *
     * @param bucketName the name of the bucket
     * @return the RateLimitInfo for the bucket, or RateLimitInfo.empty() if not found
     * @throws NullPointerException if bucketName is null
     * @since 1.1.0
     */
    public RateLimitInfo getBucketInfo(String bucketName) {
        Objects.requireNonNull(bucketName, "bucketName must not be null");
        lock.readLock().lock();
        try {
            return buckets.getOrDefault(bucketName, RateLimitInfo.empty());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets all bucket names currently tracked.
     *
     * @return unmodifiable set of bucket names
     * @since 1.1.0
     */
    public Set<String> getAllBuckets() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(buckets.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the name of the bucket requiring the longest wait time.
     * Returns null if no buckets exist or all buckets allow requests.
     *
     * @return the most restrictive bucket name, or null if none
     * @since 1.1.0
     */
    public String getMostRestrictiveBucket() {
        Instant now = Instant.now();
        lock.readLock().lock();
        try {
            String mostRestrictive = null;
            long maxScore = Long.MIN_VALUE;
            for (Map.Entry<String, RateLimitInfo> entry : buckets.entrySet()) {
                long score = scoreInfo(entry.getValue(), now);
                if (score == Long.MIN_VALUE) continue;
                if (mostRestrictive == null || score > maxScore) {
                    maxScore = score;
                    mostRestrictive = entry.getKey();
                }
            }
            return mostRestrictive;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Scores a bucket's restrictiveness for comparison purposes.
     * Returns {@link Long#MIN_VALUE} for invalid or stale buckets (skip them).
     * Higher scores mean more restrictive.
     */
    private static long scoreInfo(RateLimitInfo info, Instant now) {
        if (!info.hasValidData() || !now.isBefore(info.resetTime())) {
            return Long.MIN_VALUE;
        }
        if (info.isLimitExceeded()) {
            return info.retryAfter() > 0 ? info.retryAfter() : info.secondsUntilReset(now);
        }
        return (long) ((1.0 - (double) info.remaining() / info.limit()) * 100);
    }

    /**
     * Helper method to get the most restrictive RateLimitInfo.
     * Used by getCurrentInfo() for backwards compatibility.
     *
     * @param now the current time
     * @return the most restrictive RateLimitInfo
     */
    private RateLimitInfo getMostRestrictiveInfo(Instant now) {
        return findMostRestrictiveInfo(buckets.values(), now);
    }

    /**
     * Finds the most restrictive RateLimitInfo from a collection of buckets.
     * Static utility used by both RateLimitState and StateSnapshot for consistent scoring.
     *
     * @param buckets the bucket values to evaluate
     * @param now the current time
     * @return the most restrictive RateLimitInfo, or empty if none found
     */
    private static RateLimitInfo findMostRestrictiveInfo(Iterable<RateLimitInfo> buckets, Instant now) {
        RateLimitInfo fallback = null;
        RateLimitInfo mostRestrictive = null;
        long maxScore = Long.MIN_VALUE;

        for (RateLimitInfo info : buckets) {
            long score = scoreInfo(info, now);
            if (score == Long.MIN_VALUE) {
                if (fallback == null) fallback = info;
                continue;
            }
            if (mostRestrictive == null || score > maxScore) {
                maxScore = score;
                mostRestrictive = info;
            }
        }

        return mostRestrictive != null ? mostRestrictive : (fallback != null ? fallback : RateLimitInfo.empty());
    }

    /**
     * Resets the state to empty.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            this.buckets = new HashMap<>(4); // Typical bucket counts are small (1-5)
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
            return new StateSnapshot(host, endpoint, new HashMap<>(buckets), lastUpdated);
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
                    ", buckets=" + buckets +
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
     * @param buckets the rate limit information for all buckets
     * @param lastUpdated the last updated time
     */
    public record StateSnapshot(
            String host,
            String endpoint,
            Map<String, RateLimitInfo> buckets,
            Instant lastUpdated
    ) {
        public StateSnapshot {
            Objects.requireNonNull(host, "host must not be null");
            Objects.requireNonNull(endpoint, "endpoint must not be null");
            Objects.requireNonNull(buckets, "buckets must not be null");
            Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
        }

        /**
         * Gets the rate limit info for backwards compatibility.
         * Returns the most restrictive bucket.
         *
         * @return the most restrictive RateLimitInfo
         * @deprecated Use buckets() instead for multi-bucket support
         */
        @Deprecated(since = "1.1.0")
        public RateLimitInfo info() {
            return findMostRestrictiveInfo(buckets.values(), Instant.now());
        }
    }
}

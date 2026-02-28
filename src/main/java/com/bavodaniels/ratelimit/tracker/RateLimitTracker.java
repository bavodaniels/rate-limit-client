package com.bavodaniels.ratelimit.tracker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe tracker for rate limit states across different hosts and endpoints.
 * Uses ConcurrentHashMap for lock-free reads and atomic updates.
 *
 * @since 1.0.0
 */
@Component
public class RateLimitTracker {

    private final ConcurrentHashMap<String, RateLimitState> rateLimits = new ConcurrentHashMap<>();
    private final Duration waitThreshold;

    /**
     * Creates a new RateLimitTracker with default wait threshold of 5 seconds.
     */
    public RateLimitTracker() {
        this(Duration.ofSeconds(5));
    }

    /**
     * Creates a new RateLimitTracker with specified wait threshold.
     *
     * @param waitThreshold the maximum time to wait before throwing an exception
     */
    public RateLimitTracker(Duration waitThreshold) {
        this.waitThreshold = waitThreshold;
    }

    /**
     * Updates the rate limit state for a given key.
     * Uses atomic compute operation to ensure thread-safety.
     *
     * @param key the key (typically "host:endpoint" or just "host")
     * @param limit the rate limit (max requests allowed)
     * @param remaining the number of requests remaining
     * @param resetTime when the rate limit will reset
     */
    public void updateRateLimit(String key, int limit, int remaining, Instant resetTime) {
        rateLimits.compute(key, (k, oldState) -> {
            // Always update with new information from the server
            return RateLimitState.of(limit, remaining, resetTime);
        });
    }

    /**
     * Checks if a request should proceed or be blocked based on rate limit state.
     * If the wait time is less than the threshold, this method will block until the reset.
     * If the wait time exceeds the threshold, a RateLimitException is thrown.
     *
     * @param key the key (typically "host:endpoint" or just "host")
     * @return true if the request can proceed, false if there's no rate limit info
     * @throws RateLimitException if rate limited and wait time exceeds threshold
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public boolean checkAndWait(String key) throws RateLimitException, InterruptedException {
        RateLimitState state = rateLimits.get(key);

        // No rate limit info - allow request
        if (state == null) {
            return true;
        }

        // Rate limit expired - allow request
        if (state.isExpired()) {
            return true;
        }

        // Not rate limited - allow request
        if (!state.isLimited()) {
            return true;
        }

        // Rate limited - check wait time
        long waitTimeMillis = state.getWaitTimeMillis();

        if (waitTimeMillis <= 0) {
            // Reset time has passed
            return true;
        }

        if (waitTimeMillis > waitThreshold.toMillis()) {
            // Wait time exceeds threshold - throw exception
            throw new RateLimitException(
                String.format("Rate limit exceeded for %s. Reset in %d ms (threshold: %d ms)",
                    key, waitTimeMillis, waitThreshold.toMillis())
            );
        }

        // Wait time within threshold - block and wait
        TimeUnit.MILLISECONDS.sleep(waitTimeMillis);
        return true;
    }

    /**
     * Gets the current rate limit state for a key.
     *
     * @param key the key to look up
     * @return the current RateLimitState or null if not found
     */
    public RateLimitState getState(String key) {
        return rateLimits.get(key);
    }

    /**
     * Removes expired rate limit entries.
     * This method is called automatically every minute by Spring's scheduler.
     * Can also be called manually for testing or immediate cleanup.
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupExpiredEntries() {
        rateLimits.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Clears all rate limit state. Useful for testing.
     */
    public void clear() {
        rateLimits.clear();
    }

    /**
     * Gets the number of tracked rate limits.
     *
     * @return the number of entries in the tracker
     */
    public int size() {
        return rateLimits.size();
    }

    /**
     * Builds a key from host and endpoint.
     *
     * @param host the host (e.g., "api.example.com")
     * @param endpoint the endpoint (e.g., "/users")
     * @return the combined key (e.g., "api.example.com:/users")
     */
    public static String buildKey(String host, String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return host;
        }
        return host + ":" + endpoint;
    }
}

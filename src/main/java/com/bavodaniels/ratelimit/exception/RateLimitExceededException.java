package com.bavodaniels.ratelimit.exception;

/**
 * Exception thrown when rate limit is exceeded and the wait time exceeds the configured threshold.
 *
 * @since 1.0.0
 */
public class RateLimitExceededException extends RuntimeException {

    private final String host;
    private final long waitTimeMillis;
    private final long thresholdMillis;

    /**
     * Creates a new RateLimitExceededException.
     *
     * @param host the host that rate limited the request
     * @param waitTimeMillis the required wait time in milliseconds
     * @param thresholdMillis the maximum acceptable wait time in milliseconds
     */
    public RateLimitExceededException(String host, long waitTimeMillis, long thresholdMillis) {
        super(String.format("Rate limit exceeded for host '%s'. Required wait time %dms exceeds threshold %dms",
                host, waitTimeMillis, thresholdMillis));
        this.host = host;
        this.waitTimeMillis = waitTimeMillis;
        this.thresholdMillis = thresholdMillis;
    }

    /**
     * Gets the host that rate limited the request.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the required wait time in milliseconds.
     *
     * @return the wait time
     */
    public long getWaitTimeMillis() {
        return waitTimeMillis;
    }

    /**
     * Gets the maximum acceptable wait time in milliseconds.
     *
     * @return the threshold
     */
    public long getThresholdMillis() {
        return thresholdMillis;
    }
}

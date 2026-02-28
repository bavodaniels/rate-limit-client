package com.bavodaniels.ratelimit.exception;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Exception thrown when rate limit is exceeded.
 * <p>
 * This exception includes comprehensive metadata about the rate limit violation,
 * including the affected host and endpoint, retry timing information, and actionable
 * error messages to help clients handle rate limiting appropriately.
 * <p>
 * This exception is serializable to support distributed systems and remote exception handling.
 *
 * @since 1.0.0
 */
public class RateLimitExceededException extends RuntimeException implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String host;
    private final String endpoint;
    private final Instant retryAfter;
    private final Duration waitDuration;

    /**
     * Creates a new RateLimitExceededException with full metadata.
     *
     * @param host the host that rate limited the request
     * @param endpoint the endpoint that was rate limited
     * @param retryAfter the time after which the request can be retried
     * @param waitDuration the duration to wait before retrying
     */
    public RateLimitExceededException(String host, String endpoint, Instant retryAfter, Duration waitDuration) {
        super(buildMessage(host, endpoint, retryAfter, waitDuration));
        this.host = host;
        this.endpoint = endpoint;
        this.retryAfter = retryAfter;
        this.waitDuration = waitDuration;
    }

    /**
     * Creates a new RateLimitExceededException with a custom message and metadata.
     *
     * @param message the custom error message
     * @param host the host that rate limited the request
     * @param endpoint the endpoint that was rate limited
     * @param retryAfter the time after which the request can be retried
     * @param waitDuration the duration to wait before retrying
     */
    public RateLimitExceededException(String message, String host, String endpoint, Instant retryAfter, Duration waitDuration) {
        super(message);
        this.host = host;
        this.endpoint = endpoint;
        this.retryAfter = retryAfter;
        this.waitDuration = waitDuration;
    }

    /**
     * Builds a clear, actionable error message from the rate limit metadata.
     *
     * @param host the host that rate limited the request
     * @param endpoint the endpoint that was rate limited
     * @param retryAfter the time after which the request can be retried
     * @param waitDuration the duration to wait before retrying
     * @return the formatted error message
     */
    private static String buildMessage(String host, String endpoint, Instant retryAfter, Duration waitDuration) {
        return String.format(
                "Rate limit exceeded for '%s%s'. Retry after %s (wait %d seconds). " +
                "Please reduce request rate or implement exponential backoff.",
                host,
                endpoint,
                retryAfter,
                waitDuration.toSeconds()
        );
    }

    /**
     * Gets the host that rate limited the request.
     *
     * @return the host (never null)
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the endpoint that was rate limited.
     *
     * @return the endpoint (never null)
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Gets the time after which the request can be retried.
     *
     * @return the retry-after time (never null)
     */
    public Instant getRetryAfter() {
        return retryAfter;
    }

    /**
     * Gets the duration to wait before retrying.
     *
     * @return the wait duration (never null)
     */
    public Duration getWaitDuration() {
        return waitDuration;
    }
}

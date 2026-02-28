package com.bavodaniels.ratelimit.tracker;

/**
 * Exception thrown when a request is rate limited and the wait time exceeds the configured threshold.
 *
 * @since 1.0.0
 */
public class RateLimitException extends Exception {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}

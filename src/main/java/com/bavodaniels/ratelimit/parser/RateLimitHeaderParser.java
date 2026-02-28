package com.bavodaniels.ratelimit.parser;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parser for rate limit headers from various API providers.
 * Supports multiple header formats including:
 * <ul>
 *   <li>X-RateLimit-* (GitHub, Twitter/X, generic)</li>
 *   <li>RateLimit-* (RFC draft format)</li>
 *   <li>Retry-After (both seconds and HTTP date formats)</li>
 *   <li>Provider-specific formats (GitHub, Twitter/X, Stripe)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class RateLimitHeaderParser {

    // Standard X-RateLimit headers
    private static final String X_RATELIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String X_RATELIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String X_RATELIMIT_RESET = "X-RateLimit-Reset";

    // RFC draft format headers
    private static final String RATELIMIT_LIMIT = "RateLimit-Limit";
    private static final String RATELIMIT_REMAINING = "RateLimit-Remaining";
    private static final String RATELIMIT_RESET = "RateLimit-Reset";

    // Retry-After header
    private static final String RETRY_AFTER = "Retry-After";

    // GitHub-specific headers
    private static final String X_RATELIMIT_RESOURCE = "X-RateLimit-Resource";
    private static final String X_RATELIMIT_USED = "X-RateLimit-Used";

    // Stripe-specific headers
    private static final String STRIPE_RATELIMIT_LIMIT = "Stripe-RateLimit-Limit";
    private static final String STRIPE_RATELIMIT_REMAINING = "Stripe-RateLimit-Remaining";
    private static final String STRIPE_RATELIMIT_RESET = "Stripe-RateLimit-Reset";

    /**
     * Parses the rate limit (maximum requests allowed) from headers.
     * Checks multiple header formats in order of preference.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the rate limit, or empty if not found or malformed
     */
    public Optional<Long> parseLimit(HeaderValueProvider headerValue) {
        return parsePositiveLong(headerValue, X_RATELIMIT_LIMIT)
                .or(() -> parsePositiveLong(headerValue, RATELIMIT_LIMIT))
                .or(() -> parsePositiveLong(headerValue, STRIPE_RATELIMIT_LIMIT));
    }

    /**
     * Parses the remaining requests count from headers.
     * Checks multiple header formats in order of preference.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the remaining count, or empty if not found or malformed
     */
    public Optional<Long> parseRemaining(HeaderValueProvider headerValue) {
        return parsePositiveLong(headerValue, X_RATELIMIT_REMAINING)
                .or(() -> parsePositiveLong(headerValue, RATELIMIT_REMAINING))
                .or(() -> parsePositiveLong(headerValue, STRIPE_RATELIMIT_REMAINING));
    }

    /**
     * Parses the rate limit reset time from headers.
     * Supports Unix timestamp (seconds since epoch) format.
     * Checks multiple header formats in order of preference.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the reset time as Instant, or empty if not found or malformed
     */
    public Optional<Instant> parseReset(HeaderValueProvider headerValue) {
        return parseUnixTimestamp(headerValue, X_RATELIMIT_RESET)
                .or(() -> parseUnixTimestamp(headerValue, RATELIMIT_RESET))
                .or(() -> parseUnixTimestamp(headerValue, STRIPE_RATELIMIT_RESET));
    }

    /**
     * Parses the Retry-After header.
     * Supports both formats:
     * <ul>
     *   <li>Delay-seconds: integer number of seconds to wait</li>
     *   <li>HTTP-date: RFC 7231 date format</li>
     * </ul>
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the retry-after time as Instant, or empty if not found or malformed
     */
    public Optional<Instant> parseRetryAfter(HeaderValueProvider headerValue) {
        String value = headerValue.getHeader(RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        // Try parsing as seconds (delay-seconds format)
        try {
            long seconds = Long.parseLong(value.trim());
            if (seconds >= 0) {
                return Optional.of(Instant.now().plusSeconds(seconds));
            }
        } catch (NumberFormatException e) {
            // Not a number, try HTTP-date format
        }

        // Try parsing as HTTP-date (RFC 7231 format)
        return parseHttpDate(value);
    }

    /**
     * Parses GitHub-specific rate limit resource type.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the resource type, or empty if not found
     */
    public Optional<String> parseGitHubResource(HeaderValueProvider headerValue) {
        String value = headerValue.getHeader(X_RATELIMIT_RESOURCE);
        return value != null && !value.isBlank() ? Optional.of(value.trim()) : Optional.empty();
    }

    /**
     * Parses GitHub-specific used count.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the used count, or empty if not found or malformed
     */
    public Optional<Long> parseGitHubUsed(HeaderValueProvider headerValue) {
        return parsePositiveLong(headerValue, X_RATELIMIT_USED);
    }

    /**
     * Parses a positive long value from a header.
     *
     * @param headerValue function to retrieve header value by name
     * @param headerName name of the header to parse
     * @return Optional containing the parsed value, or empty if not found or malformed
     */
    private Optional<Long> parsePositiveLong(HeaderValueProvider headerValue, String headerName) {
        String value = headerValue.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            long parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses a Unix timestamp (seconds since epoch) from a header.
     *
     * @param headerValue function to retrieve header value by name
     * @param headerName name of the header to parse
     * @return Optional containing the parsed Instant, or empty if not found or malformed
     */
    private Optional<Instant> parseUnixTimestamp(HeaderValueProvider headerValue, String headerName) {
        String value = headerValue.getHeader(headerName);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            long timestamp = Long.parseLong(value.trim());
            // Validate reasonable timestamp (not negative, not too far in the future)
            if (timestamp >= 0 && timestamp <= 9999999999L) { // Max ~year 2286
                return Optional.of(Instant.ofEpochSecond(timestamp));
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses an HTTP-date format timestamp (RFC 7231).
     * Supports formats like: "Wed, 21 Oct 2015 07:28:00 GMT"
     *
     * @param dateValue the date string to parse
     * @return Optional containing the parsed Instant, or empty if malformed
     */
    private Optional<Instant> parseHttpDate(String dateValue) {
        if (dateValue == null || dateValue.isBlank()) {
            return Optional.empty();
        }

        try {
            // RFC 7231 HTTP-date format
            ZonedDateTime zdt = ZonedDateTime.parse(dateValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            return Optional.of(zdt.toInstant());
        } catch (DateTimeParseException e) {
            // Try ISO 8601 format as fallback
            try {
                Instant instant = Instant.parse(dateValue.trim());
                return Optional.of(instant);
            } catch (DateTimeParseException ex) {
                return Optional.empty();
            }
        }
    }

    /**
     * Functional interface for retrieving header values.
     * Implementations should handle case-insensitive header name lookup.
     */
    @FunctionalInterface
    public interface HeaderValueProvider {
        /**
         * Retrieves a header value by name.
         *
         * @param headerName the header name (case-insensitive)
         * @return the header value, or null if not found
         */
        String getHeader(String headerName);
    }
}

package be.bavodaniels.ratelimit.parser;

import be.bavodaniels.ratelimit.model.RateLimitInfo;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>Also supports multi-bucket rate limiting where APIs provide multiple rate limits
 * (e.g., daily limit, hourly limit, per-resource limit). Multi-bucket headers use the pattern:
 * {@code X-RateLimit-{BucketName}-Limit}, {@code X-RateLimit-{BucketName}-Remaining},
 * {@code X-RateLimit-{BucketName}-Reset}.</p>
 *
 * <p>Example multi-bucket headers:
 * <pre>
 * X-RateLimit-AppDay-Limit: 10000000
 * X-RateLimit-AppDay-Remaining: 9999999
 * X-RateLimit-AppDay-Reset: 1735689600
 *
 * X-RateLimit-Session-Limit: 120
 * X-RateLimit-Session-Remaining: 75
 * X-RateLimit-Session-Reset: 1735689600
 * </pre>
 * </p>
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

    // Pattern for multi-bucket headers: X-RateLimit-{BucketName}-Limit
    private static final Pattern BUCKET_LIMIT_PATTERN = Pattern.compile(
        "^X-RateLimit-([A-Za-z0-9_-]+)-Limit$",
        Pattern.CASE_INSENSITIVE
    );

    // Default bucket name for single-bucket (legacy) headers
    private static final String DEFAULT_BUCKET = "default";

    /**
     * Parses the rate limit (maximum requests allowed) from headers.
     * Checks multiple header formats in order of preference.
     * Only falls back to alternative formats if the preferred header is not present.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the rate limit, or empty if not found or malformed
     */
    public Optional<Long> parseLimit(HeaderValueProvider headerValue) {
        // Check if X-RateLimit-Limit exists
        if (headerValue.getHeader(X_RATELIMIT_LIMIT) != null) {
            return parsePositiveLong(headerValue, X_RATELIMIT_LIMIT);
        }

        // Fallback to RateLimit-Limit if X-RateLimit-Limit not present
        if (headerValue.getHeader(RATELIMIT_LIMIT) != null) {
            return parsePositiveLong(headerValue, RATELIMIT_LIMIT);
        }

        // Fallback to Stripe format if neither of the above are present
        return parsePositiveLong(headerValue, STRIPE_RATELIMIT_LIMIT);
    }

    /**
     * Parses the remaining requests count from headers.
     * Checks multiple header formats in order of preference.
     * Only falls back to alternative formats if the preferred header is not present.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the remaining count, or empty if not found or malformed
     */
    public Optional<Long> parseRemaining(HeaderValueProvider headerValue) {
        // Check if X-RateLimit-Remaining exists
        if (headerValue.getHeader(X_RATELIMIT_REMAINING) != null) {
            return parsePositiveLong(headerValue, X_RATELIMIT_REMAINING);
        }

        // Fallback to RateLimit-Remaining if X-RateLimit-Remaining not present
        if (headerValue.getHeader(RATELIMIT_REMAINING) != null) {
            return parsePositiveLong(headerValue, RATELIMIT_REMAINING);
        }

        // Fallback to Stripe format if neither of the above are present
        return parsePositiveLong(headerValue, STRIPE_RATELIMIT_REMAINING);
    }

    /**
     * Parses the rate limit reset time from headers.
     * Supports Unix timestamp (seconds since epoch) format.
     * Checks multiple header formats in order of preference.
     * Only falls back to alternative formats if the preferred header is not present.
     *
     * @param headerValue function to retrieve header value by name (case-insensitive)
     * @return Optional containing the reset time as Instant, or empty if not found or malformed
     */
    public Optional<Instant> parseReset(HeaderValueProvider headerValue) {
        // Check if X-RateLimit-Reset exists
        if (headerValue.getHeader(X_RATELIMIT_RESET) != null) {
            return parseUnixTimestamp(headerValue, X_RATELIMIT_RESET);
        }

        // Fallback to RateLimit-Reset if X-RateLimit-Reset not present
        if (headerValue.getHeader(RATELIMIT_RESET) != null) {
            return parseUnixTimestamp(headerValue, RATELIMIT_RESET);
        }

        // Fallback to Stripe format if neither of the above are present
        return parseUnixTimestamp(headerValue, STRIPE_RATELIMIT_RESET);
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
     * Parses all rate limit buckets from headers.
     * <p>
     * Supports multi-bucket headers with pattern: X-RateLimit-{BucketName}-Limit,
     * X-RateLimit-{BucketName}-Remaining, X-RateLimit-{BucketName}-Reset.
     * </p>
     * <p>
     * Also maintains backwards compatibility with single-bucket (legacy) headers:
     * X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset.
     * These are mapped to the "default" bucket.
     * </p>
     * <p>
     * Bucket names are normalized to lowercase for consistent lookup and storage.
     * For example, "AppDay", "appday", and "APPDAY" are all normalized to "appday".
     * </p>
     *
     * @param headerValueProvider function to retrieve header value by name (case-insensitive)
     * @return Map of bucket names to RateLimitInfo (bucket names are lowercase).
     *         Empty map if no rate limit headers found.
     *         For single-bucket headers, returns a map with key "default".
     * @since 1.1.0
     */
    public Map<String, RateLimitInfo> parseAllBuckets(HeaderValueProvider headerValueProvider) {
        // First, try to detect multi-bucket headers
        Set<String> bucketNames = detectBucketNames(headerValueProvider);

        Map<String, RateLimitInfo> result = new LinkedHashMap<>();

        if (!bucketNames.isEmpty()) {
            // Parse multi-bucket headers
            for (String bucketName : bucketNames) {
                RateLimitInfo info = parseBucketInfo(headerValueProvider, bucketName);
                if (info.hasValidData()) {
                    result.put(bucketName, info);
                }
            }
        } else {
            // Fall back to single-bucket (legacy) headers
            RateLimitInfo defaultInfo = parseLegacyHeaders(headerValueProvider);
            if (defaultInfo.hasValidData()) {
                result.put(DEFAULT_BUCKET, defaultInfo);
            }
        }

        return result;
    }

    /**
     * Detects all bucket names from headers by finding headers matching the pattern
     * {@code X-RateLimit-{BucketName}-Limit}. Requires the provider to implement
     * {@link AllHeadersProvider} for header enumeration; returns an empty set otherwise.
     *
     * @param headerValueProvider function to retrieve header values
     * @return Set of bucket names found in headers (preserving case from first occurrence)
     */
    private Set<String> detectBucketNames(HeaderValueProvider headerValueProvider) {
        Set<String> bucketNames = new LinkedHashSet<>();
        Map<String, String> canonicalNames = new HashMap<>(); // Maps lowercase to original case

        if (headerValueProvider instanceof AllHeadersProvider allHeadersProvider) {
            for (String headerName : allHeadersProvider.getAllHeaders().keySet()) {
                Matcher matcher = BUCKET_LIMIT_PATTERN.matcher(headerName);
                if (matcher.matches()) {
                    String bucketName = matcher.group(1);
                    String lowerBucketName = bucketName.toLowerCase();

                    // Use the first occurrence's casing
                    if (!canonicalNames.containsKey(lowerBucketName)) {
                        canonicalNames.put(lowerBucketName, bucketName);
                        bucketNames.add(bucketName);
                    }
                }
            }
        }

        return bucketNames;
    }

    /**
     * Parses rate limit information for a specific bucket.
     *
     * @param headerValueProvider function to retrieve header values
     * @param bucketName the bucket name
     * @return RateLimitInfo for the bucket
     */
    private RateLimitInfo parseBucketInfo(HeaderValueProvider headerValueProvider, String bucketName) {
        String limitHeader = "X-RateLimit-" + bucketName + "-Limit";
        String remainingHeader = "X-RateLimit-" + bucketName + "-Remaining";
        String resetHeader = "X-RateLimit-" + bucketName + "-Reset";

        Optional<Long> limit = parsePositiveLong(headerValueProvider, limitHeader);
        Optional<Long> remaining = parsePositiveLong(headerValueProvider, remainingHeader);
        Optional<Instant> reset = parseUnixTimestamp(headerValueProvider, resetHeader);

        long limitValue = limit.orElse(0L);
        long remainingValue = remaining.orElse(0L);

        // Ensure remaining doesn't exceed limit
        if (remainingValue > limitValue) {
            remainingValue = limitValue;
        }

        return RateLimitInfo.builder()
            .limit(limitValue)
            .remaining(remainingValue)
            .resetTime(reset.orElse(Instant.EPOCH))
            .retryAfter(0L)
            .build();
    }

    /**
     * Parses legacy single-bucket headers (X-RateLimit-Limit, etc.).
     *
     * @param headerValueProvider function to retrieve header values
     * @return RateLimitInfo from legacy headers
     */
    private RateLimitInfo parseLegacyHeaders(HeaderValueProvider headerValueProvider) {
        Optional<Long> limit = parseLimit(headerValueProvider);
        Optional<Long> remaining = parseRemaining(headerValueProvider);
        Optional<Instant> reset = parseReset(headerValueProvider);
        Optional<Instant> retryAfter = parseRetryAfter(headerValueProvider);

        long retryAfterSeconds = 0L;
        Instant resetTime = Instant.EPOCH;

        if (retryAfter.isPresent()) {
            retryAfterSeconds = Math.max(0, retryAfter.get().getEpochSecond() - Instant.now().getEpochSecond());
            // If Retry-After is present but Reset is not, use Retry-After as the reset time
            if (!reset.isPresent()) {
                resetTime = retryAfter.get();
            } else {
                resetTime = reset.get();
            }
        } else if (reset.isPresent()) {
            resetTime = reset.get();
            retryAfterSeconds = Math.max(0, reset.get().getEpochSecond() - Instant.now().getEpochSecond());
        }

        // Use explicit limit if provided, otherwise use remaining count or retryAfter as indicator
        long limitValue = limit.orElse(0L);
        long remainingValue = remaining.orElse(0L);

        // If we have remaining=0 or retryAfter but no explicit limit, use a default limit of 1
        // This ensures the rate limit state is considered valid (hasValidData() will return true)
        // Only do this if we actually parsed a remaining or retry-after header
        if (limitValue == 0 && ((remaining.isPresent() && remainingValue == 0) || retryAfterSeconds > 0)) {
            limitValue = 1;
        }

        return RateLimitInfo.builder()
            .limit(limitValue)
            .remaining(remainingValue)
            .resetTime(resetTime)
            .retryAfter(retryAfterSeconds)
            .build();
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

        // Reject values containing control characters or invalid characters
        if (containsInvalidCharacters(value)) {
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

        // Reject values containing control characters or invalid characters
        if (containsInvalidCharacters(value)) {
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
     * Checks if a string contains invalid characters for numeric header values.
     * Rejects control characters, null bytes, and other non-numeric characters except whitespace.
     *
     * @param value the string to check
     * @return true if the string contains invalid characters
     */
    private boolean containsInvalidCharacters(String value) {
        if (value == null) {
            return false;
        }

        // Check all characters in the original value
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            // Allow: digits, spaces, and tabs
            if (c == ' ' || c == '\t' || Character.isDigit(c)) {
                continue; // Valid character
            }

            // Reject everything else: control characters, plus signs, letters, etc.
            if (c == '+') {
                return true; // Explicitly reject plus sign
            }
            if (Character.isISOControl(c)) {
                return true; // Reject all control characters (includes \0, \n, \r, etc.)
            }

            // Reject any other character (letters, special symbols, etc.)
            return true;
        }

        return false;
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

    /**
     * Extended interface for header value providers that can enumerate all headers.
     * This is required for multi-bucket header detection in {@link #parseAllBuckets(HeaderValueProvider)}.
     *
     * @since 1.1.0
     */
    public interface AllHeadersProvider extends HeaderValueProvider {
        /**
         * Retrieves all headers as a map.
         *
         * @return Map of header names to values. Header names should be preserved as-is
         *         (case-sensitive) to allow pattern matching.
         */
        Map<String, String> getAllHeaders();
    }
}

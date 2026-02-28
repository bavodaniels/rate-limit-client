package com.bavodaniels.ratelimit.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for RateLimitHeaderParser.
 * Tests all supported header formats and edge cases.
 */
class RateLimitHeaderParserTest {

    private RateLimitHeaderParser parser;

    @BeforeEach
    void setUp() {
        parser = new RateLimitHeaderParser();
    }

    /**
     * Creates a case-insensitive header value provider from a map.
     */
    private RateLimitHeaderParser.HeaderValueProvider createProvider(Map<String, String> headers) {
        return headerName -> {
            // Case-insensitive lookup
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)) {
                    return entry.getValue();
                }
            }
            return null;
        };
    }

    @Nested
    @DisplayName("parseLimit() tests")
    class ParseLimitTests {

        @Test
        @DisplayName("should parse X-RateLimit-Limit header")
        void shouldParseXRateLimitLimit() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should parse RateLimit-Limit header (RFC draft)")
        void shouldParseRateLimitLimit() {
            Map<String, String> headers = Map.of("RateLimit-Limit", "100");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(100L, result.get());
        }

        @Test
        @DisplayName("should parse Stripe-RateLimit-Limit header")
        void shouldParseStripeRateLimitLimit() {
            Map<String, String> headers = Map.of("Stripe-RateLimit-Limit", "1000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(1000L, result.get());
        }

        @Test
        @DisplayName("should prefer X-RateLimit-Limit over other formats")
        void shouldPreferXRateLimitLimit() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "5000",
                    "RateLimit-Limit", "100",
                    "Stripe-RateLimit-Limit", "1000"
            );
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should be case-insensitive for header names")
        void shouldBeCaseInsensitive() {
            Map<String, String> headers = Map.of("x-ratelimit-limit", "5000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should handle whitespace in header value")
        void shouldHandleWhitespace() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "  5000  ");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should return empty for missing header")
        void shouldReturnEmptyForMissingHeader() {
            Map<String, String> headers = Map.of();
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for malformed value")
        void shouldReturnEmptyForMalformedValue() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "not-a-number");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for negative value")
        void shouldReturnEmptyForNegativeValue() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "-100");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for empty string")
        void shouldReturnEmptyForEmptyString() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should parse zero as valid limit")
        void shouldParseZero() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "0");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(0L, result.get());
        }

        @Test
        @DisplayName("should parse large numbers")
        void shouldParseLargeNumbers() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "9223372036854775807"); // Long.MAX_VALUE
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Long.MAX_VALUE, result.get());
        }
    }

    @Nested
    @DisplayName("parseRemaining() tests")
    class ParseRemainingTests {

        @Test
        @DisplayName("should parse X-RateLimit-Remaining header")
        void shouldParseXRateLimitRemaining() {
            Map<String, String> headers = Map.of("X-RateLimit-Remaining", "4999");
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(4999L, result.get());
        }

        @Test
        @DisplayName("should parse RateLimit-Remaining header (RFC draft)")
        void shouldParseRateLimitRemaining() {
            Map<String, String> headers = Map.of("RateLimit-Remaining", "99");
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(99L, result.get());
        }

        @Test
        @DisplayName("should parse Stripe-RateLimit-Remaining header")
        void shouldParseStripeRateLimitRemaining() {
            Map<String, String> headers = Map.of("Stripe-RateLimit-Remaining", "999");
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(999L, result.get());
        }

        @Test
        @DisplayName("should prefer X-RateLimit-Remaining over other formats")
        void shouldPreferXRateLimitRemaining() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Remaining", "4999",
                    "RateLimit-Remaining", "99",
                    "Stripe-RateLimit-Remaining", "999"
            );
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(4999L, result.get());
        }

        @Test
        @DisplayName("should parse zero remaining")
        void shouldParseZeroRemaining() {
            Map<String, String> headers = Map.of("X-RateLimit-Remaining", "0");
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(0L, result.get());
        }

        @Test
        @DisplayName("should return empty for missing header")
        void shouldReturnEmptyForMissingHeader() {
            Map<String, String> headers = Map.of();
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for malformed value")
        void shouldReturnEmptyForMalformedValue() {
            Map<String, String> headers = Map.of("X-RateLimit-Remaining", "invalid");
            Optional<Long> result = parser.parseRemaining(createProvider(headers));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("parseReset() tests")
    class ParseResetTests {

        @Test
        @DisplayName("should parse X-RateLimit-Reset header as Unix timestamp")
        void shouldParseXRateLimitReset() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "1609459200"); // 2021-01-01 00:00:00 UTC
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), result.get());
        }

        @Test
        @DisplayName("should parse RateLimit-Reset header (RFC draft)")
        void shouldParseRateLimitReset() {
            Map<String, String> headers = Map.of("RateLimit-Reset", "1609459200");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), result.get());
        }

        @Test
        @DisplayName("should parse Stripe-RateLimit-Reset header")
        void shouldParseStripeRateLimitReset() {
            Map<String, String> headers = Map.of("Stripe-RateLimit-Reset", "1609459200");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), result.get());
        }

        @Test
        @DisplayName("should prefer X-RateLimit-Reset over other formats")
        void shouldPreferXRateLimitReset() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Reset", "1609459200",
                    "RateLimit-Reset", "1609459300",
                    "Stripe-RateLimit-Reset", "1609459400"
            );
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), result.get());
        }

        @Test
        @DisplayName("should handle whitespace in timestamp")
        void shouldHandleWhitespace() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "  1609459200  ");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), result.get());
        }

        @Test
        @DisplayName("should return empty for missing header")
        void shouldReturnEmptyForMissingHeader() {
            Map<String, String> headers = Map.of();
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for malformed timestamp")
        void shouldReturnEmptyForMalformedTimestamp() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "not-a-timestamp");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for negative timestamp")
        void shouldReturnEmptyForNegativeTimestamp() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "-100");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for unreasonably large timestamp")
        void shouldReturnEmptyForUnreasonablyLargeTimestamp() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "99999999999"); // Beyond year 2286
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should parse timestamp at upper boundary")
        void shouldParseTimestampAtUpperBoundary() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "9999999999"); // Just within year 2286
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(9999999999L), result.get());
        }
    }

    @Nested
    @DisplayName("parseRetryAfter() tests")
    class ParseRetryAfterTests {

        @Test
        @DisplayName("should parse Retry-After as seconds")
        void shouldParseRetryAfterAsSeconds() {
            Map<String, String> headers = Map.of("Retry-After", "120");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            // Should be approximately now + 120 seconds (within 5 seconds tolerance)
            Instant expected = Instant.now().plusSeconds(120);
            long diff = Math.abs(result.get().getEpochSecond() - expected.getEpochSecond());
            assertTrue(diff < 5, "Retry-After timestamp should be within 5 seconds of expected");
        }

        @Test
        @DisplayName("should parse Retry-After as zero seconds")
        void shouldParseRetryAfterAsZeroSeconds() {
            Map<String, String> headers = Map.of("Retry-After", "0");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            // Should be approximately now
            Instant expected = Instant.now();
            long diff = Math.abs(result.get().getEpochSecond() - expected.getEpochSecond());
            assertTrue(diff < 5, "Retry-After timestamp should be within 5 seconds of now");
        }

        @Test
        @DisplayName("should parse Retry-After as HTTP-date (RFC 1123)")
        void shouldParseRetryAfterAsHttpDate() {
            Map<String, String> headers = Map.of("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            // Expected: 2015-10-21T07:28:00Z
            Instant expected = Instant.parse("2015-10-21T07:28:00Z");
            assertEquals(expected, result.get());
        }

        @Test
        @DisplayName("should parse Retry-After as ISO 8601 date")
        void shouldParseRetryAfterAsIso8601() {
            Map<String, String> headers = Map.of("Retry-After", "2021-01-01T00:00:00Z");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.parse("2021-01-01T00:00:00Z"), result.get());
        }

        @Test
        @DisplayName("should handle whitespace in Retry-After value")
        void shouldHandleWhitespace() {
            Map<String, String> headers = Map.of("Retry-After", "  120  ");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("should return empty for missing Retry-After header")
        void shouldReturnEmptyForMissingHeader() {
            Map<String, String> headers = Map.of();
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for malformed Retry-After value")
        void shouldReturnEmptyForMalformedValue() {
            Map<String, String> headers = Map.of("Retry-After", "invalid-date-format");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for negative seconds")
        void shouldReturnEmptyForNegativeSeconds() {
            Map<String, String> headers = Map.of("Retry-After", "-60");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for empty string")
        void shouldReturnEmptyForEmptyString() {
            Map<String, String> headers = Map.of("Retry-After", "");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for blank string")
        void shouldReturnEmptyForBlankString() {
            Map<String, String> headers = Map.of("Retry-After", "   ");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("GitHub-specific tests")
    class GitHubSpecificTests {

        @Test
        @DisplayName("should parse X-RateLimit-Resource header")
        void shouldParseGitHubResource() {
            Map<String, String> headers = Map.of("X-RateLimit-Resource", "core");
            Optional<String> result = parser.parseGitHubResource(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals("core", result.get());
        }

        @Test
        @DisplayName("should parse X-RateLimit-Used header")
        void shouldParseGitHubUsed() {
            Map<String, String> headers = Map.of("X-RateLimit-Used", "1");
            Optional<Long> result = parser.parseGitHubUsed(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(1L, result.get());
        }

        @Test
        @DisplayName("should parse complete GitHub rate limit headers")
        void shouldParseCompleteGitHubHeaders() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "5000",
                    "X-RateLimit-Remaining", "4999",
                    "X-RateLimit-Reset", "1609459200",
                    "X-RateLimit-Resource", "core",
                    "X-RateLimit-Used", "1"
            );

            var provider = createProvider(headers);

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);
            Optional<String> resource = parser.parseGitHubResource(provider);
            Optional<Long> used = parser.parseGitHubUsed(provider);

            assertTrue(limit.isPresent());
            assertEquals(5000L, limit.get());

            assertTrue(remaining.isPresent());
            assertEquals(4999L, remaining.get());

            assertTrue(reset.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), reset.get());

            assertTrue(resource.isPresent());
            assertEquals("core", resource.get());

            assertTrue(used.isPresent());
            assertEquals(1L, used.get());
        }

        @Test
        @DisplayName("should return empty for missing GitHub resource header")
        void shouldReturnEmptyForMissingResource() {
            Map<String, String> headers = Map.of();
            Optional<String> result = parser.parseGitHubResource(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for empty GitHub resource value")
        void shouldReturnEmptyForEmptyResource() {
            Map<String, String> headers = Map.of("X-RateLimit-Resource", "");
            Optional<String> result = parser.parseGitHubResource(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should trim whitespace from GitHub resource value")
        void shouldTrimResourceWhitespace() {
            Map<String, String> headers = Map.of("X-RateLimit-Resource", "  core  ");
            Optional<String> result = parser.parseGitHubResource(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals("core", result.get());
        }
    }

    @Nested
    @DisplayName("Twitter/X API tests")
    class TwitterApiTests {

        @Test
        @DisplayName("should parse Twitter/X rate limit headers")
        void shouldParseTwitterHeaders() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "15",
                    "X-RateLimit-Remaining", "14",
                    "X-RateLimit-Reset", "1609459200"
            );

            var provider = createProvider(headers);

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);

            assertTrue(limit.isPresent());
            assertEquals(15L, limit.get());

            assertTrue(remaining.isPresent());
            assertEquals(14L, remaining.get());

            assertTrue(reset.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), reset.get());
        }
    }

    @Nested
    @DisplayName("Stripe API tests")
    class StripeApiTests {

        @Test
        @DisplayName("should parse Stripe rate limit headers")
        void shouldParseStripeHeaders() {
            Map<String, String> headers = Map.of(
                    "Stripe-RateLimit-Limit", "100",
                    "Stripe-RateLimit-Remaining", "99",
                    "Stripe-RateLimit-Reset", "1609459200"
            );

            var provider = createProvider(headers);

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);

            assertTrue(limit.isPresent());
            assertEquals(100L, limit.get());

            assertTrue(remaining.isPresent());
            assertEquals(99L, remaining.get());

            assertTrue(reset.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), reset.get());
        }
    }

    @Nested
    @DisplayName("RFC draft format tests")
    class RfcDraftFormatTests {

        @Test
        @DisplayName("should parse RFC draft RateLimit headers")
        void shouldParseRfcDraftHeaders() {
            Map<String, String> headers = Map.of(
                    "RateLimit-Limit", "1000",
                    "RateLimit-Remaining", "999",
                    "RateLimit-Reset", "1609459200"
            );

            var provider = createProvider(headers);

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);

            assertTrue(limit.isPresent());
            assertEquals(1000L, limit.get());

            assertTrue(remaining.isPresent());
            assertEquals(999L, remaining.get());

            assertTrue(reset.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), reset.get());
        }
    }

    @Nested
    @DisplayName("Edge cases and error handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle null provider gracefully")
        void shouldHandleNullHeaderValue() {
            RateLimitHeaderParser.HeaderValueProvider provider = headerName -> null;

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);
            Optional<Instant> retryAfter = parser.parseRetryAfter(provider);

            assertTrue(limit.isEmpty());
            assertTrue(remaining.isEmpty());
            assertTrue(reset.isEmpty());
            assertTrue(retryAfter.isEmpty());
        }

        @Test
        @DisplayName("should handle mixed valid and invalid headers")
        void shouldHandleMixedHeaders() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "5000",
                    "X-RateLimit-Remaining", "invalid",
                    "X-RateLimit-Reset", "1609459200"
            );

            var provider = createProvider(headers);

            Optional<Long> limit = parser.parseLimit(provider);
            Optional<Long> remaining = parser.parseRemaining(provider);
            Optional<Instant> reset = parser.parseReset(provider);

            assertTrue(limit.isPresent());
            assertEquals(5000L, limit.get());

            assertTrue(remaining.isEmpty()); // Invalid value

            assertTrue(reset.isPresent());
            assertEquals(Instant.ofEpochSecond(1609459200L), reset.get());
        }

        @Test
        @DisplayName("should handle overflow in long parsing")
        void shouldHandleOverflow() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "99999999999999999999999999");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle decimal numbers as invalid")
        void shouldRejectDecimalNumbers() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000.5");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle headers with embedded special characters")
        void shouldHandleEmbeddedSpecialCharacters() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "50\n00");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Case-insensitive header lookup tests")
    class CaseInsensitiveTests {

        @Test
        @DisplayName("should handle all lowercase header names")
        void shouldHandleLowercase() {
            Map<String, String> headers = Map.of(
                    "x-ratelimit-limit", "5000",
                    "x-ratelimit-remaining", "4999",
                    "x-ratelimit-reset", "1609459200"
            );

            var provider = createProvider(headers);

            assertTrue(parser.parseLimit(provider).isPresent());
            assertTrue(parser.parseRemaining(provider).isPresent());
            assertTrue(parser.parseReset(provider).isPresent());
        }

        @Test
        @DisplayName("should handle all uppercase header names")
        void shouldHandleUppercase() {
            Map<String, String> headers = Map.of(
                    "X-RATELIMIT-LIMIT", "5000",
                    "X-RATELIMIT-REMAINING", "4999",
                    "X-RATELIMIT-RESET", "1609459200"
            );

            var provider = createProvider(headers);

            assertTrue(parser.parseLimit(provider).isPresent());
            assertTrue(parser.parseRemaining(provider).isPresent());
            assertTrue(parser.parseReset(provider).isPresent());
        }

        @Test
        @DisplayName("should handle mixed case header names")
        void shouldHandleMixedCase() {
            Map<String, String> headers = Map.of(
                    "X-RaTeLiMiT-LiMiT", "5000",
                    "x-RaTeLimit-Remaining", "4999",
                    "X-rAtElImIt-rEsEt", "1609459200"
            );

            var provider = createProvider(headers);

            assertTrue(parser.parseLimit(provider).isPresent());
            assertTrue(parser.parseRemaining(provider).isPresent());
            assertTrue(parser.parseReset(provider).isPresent());
        }
    }
}

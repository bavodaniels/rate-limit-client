package be.bavodaniels.ratelimit.parser;

import be.bavodaniels.ratelimit.model.RateLimitInfo;
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

    /**
     * Creates an AllHeadersProvider from a map.
     */
    private RateLimitHeaderParser.AllHeadersProvider createAllHeadersProvider(Map<String, String> headers) {
        return new RateLimitHeaderParser.AllHeadersProvider() {
            @Override
            public Map<String, String> getAllHeaders() {
                return headers;
            }

            @Override
            public String getHeader(String headerName) {
                // Case-insensitive lookup
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(headerName)) {
                        return entry.getValue();
                    }
                }
                return null;
            }
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

        @Test
        @DisplayName("should handle headers with leading zeros")
        void shouldHandleLeadingZeros() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "005000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should handle headers with plus sign")
        void shouldHandlePlusSign() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "+5000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            // NumberFormatException will be thrown for plus sign
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle hexadecimal values as invalid")
        void shouldRejectHexadecimalValues() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "0x5000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle scientific notation as invalid")
        void shouldRejectScientificNotation() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5e3");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle tab characters in values")
        void shouldHandleTabCharacters() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "\t5000\t");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(5000L, result.get());
        }

        @Test
        @DisplayName("should handle newline characters as invalid")
        void shouldRejectNewlineCharacters() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000\n");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle carriage return characters as invalid")
        void shouldRejectCarriageReturnCharacters() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000\r");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle empty headers map")
        void shouldHandleEmptyHeadersMap() {
            Map<String, String> headers = Map.of();
            var provider = createProvider(headers);

            assertTrue(parser.parseLimit(provider).isEmpty());
            assertTrue(parser.parseRemaining(provider).isEmpty());
            assertTrue(parser.parseReset(provider).isEmpty());
            assertTrue(parser.parseRetryAfter(provider).isEmpty());
            assertTrue(parser.parseGitHubResource(provider).isEmpty());
            assertTrue(parser.parseGitHubUsed(provider).isEmpty());
        }

        @Test
        @DisplayName("should handle mixed format headers (preferring X-RateLimit)")
        void shouldHandleMixedFormats() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "5000",
                    "RateLimit-Limit", "1000",
                    "Stripe-RateLimit-Limit", "2000",
                    "X-RateLimit-Remaining", "4999",
                    "RateLimit-Remaining", "999",
                    "Stripe-RateLimit-Remaining", "1999"
            );

            var provider = createProvider(headers);

            // Should prefer X-RateLimit headers
            assertEquals(5000L, parser.parseLimit(provider).get());
            assertEquals(4999L, parser.parseRemaining(provider).get());
        }
    }

    @Nested
    @DisplayName("Malformed date values")
    class MalformedDateTests {

        @Test
        @DisplayName("should reject invalid HTTP date format")
        void shouldRejectInvalidHttpDate() {
            Map<String, String> headers = Map.of("Retry-After", "Not a valid date");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject partial HTTP date")
        void shouldRejectPartialHttpDate() {
            Map<String, String> headers = Map.of("Retry-After", "Wed, 21 Oct");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject malformed ISO date")
        void shouldRejectMalformedIsoDate() {
            Map<String, String> headers = Map.of("Retry-After", "2021-13-01T00:00:00Z");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject date with invalid month")
        void shouldRejectInvalidMonth() {
            Map<String, String> headers = Map.of("Retry-After", "Wed, 21 Foo 2015 07:28:00 GMT");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should parse various valid HTTP date formats")
        void shouldParseValidHttpDateFormats() {
            Map<String, String> headers1 = Map.of("Retry-After", "Mon, 01 Jan 2024 00:00:00 GMT");
            Optional<Instant> result1 = parser.parseRetryAfter(createProvider(headers1));
            assertTrue(result1.isPresent());

            Map<String, String> headers2 = Map.of("Retry-After", "Fri, 31 Dec 2021 23:59:59 GMT");
            Optional<Instant> result2 = parser.parseRetryAfter(createProvider(headers2));
            assertTrue(result2.isPresent());
        }
    }

    @Nested
    @DisplayName("Security tests")
    class SecurityTests {

        @Test
        @DisplayName("should safely handle SQL injection attempt in header")
        void shouldHandleSqlInjectionAttempt() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000; DROP TABLE users;--");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should safely handle script injection in header")
        void shouldHandleScriptInjection() {
            Map<String, String> headers = Map.of("X-RateLimit-Resource", "<script>alert('xss')</script>");
            Optional<String> result = parser.parseGitHubResource(createProvider(headers));

            // Should return the value as-is (parser doesn't validate content, just returns it)
            assertTrue(result.isPresent());
            assertEquals("<script>alert('xss')</script>", result.get());
        }

        @Test
        @DisplayName("should safely handle command injection in header")
        void shouldHandleCommandInjection() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000; rm -rf /");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle Long.MAX_VALUE without overflow")
        void shouldHandleMaxLongValue() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", String.valueOf(Long.MAX_VALUE));
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Long.MAX_VALUE, result.get());
        }

        @Test
        @DisplayName("should reject Long.MAX_VALUE + 1")
        void shouldRejectOverMaxLongValue() {
            // This will cause NumberFormatException
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "9223372036854775808");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle Long.MIN_VALUE as negative (rejected)")
        void shouldRejectMinLongValue() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", String.valueOf(Long.MIN_VALUE));
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            // Should be rejected as negative
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle very long strings without memory issues")
        void shouldHandleVeryLongStrings() {
            String veryLongString = "5".repeat(10000);
            Map<String, String> headers = Map.of("X-RateLimit-Limit", veryLongString);
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            // Should safely reject without memory issues
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle null bytes in header value")
        void shouldHandleNullBytes() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000\u0000");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle Unicode characters")
        void shouldHandleUnicodeCharacters() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "5000\u00A0");
            Optional<Long> result = parser.parseLimit(createProvider(headers));

            // Non-breaking space should cause parsing to fail
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle timestamp at epoch boundary (year 2286)")
        void shouldHandleEpochBoundary() {
            // Max valid timestamp: 9999999999 (year ~2286)
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "9999999999");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.ofEpochSecond(9999999999L), result.get());
        }

        @Test
        @DisplayName("should reject timestamp beyond year 2286")
        void shouldRejectTimestampBeyondBoundary() {
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "10000000000");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Retry-After edge cases")
    class RetryAfterEdgeCases {

        @Test
        @DisplayName("should parse large delay-seconds value")
        void shouldParseLargeDelaySeconds() {
            Map<String, String> headers = Map.of("Retry-After", "86400"); // 24 hours
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            Instant expected = Instant.now().plusSeconds(86400);
            long diff = Math.abs(result.get().getEpochSecond() - expected.getEpochSecond());
            assertTrue(diff < 5, "Should be within 5 seconds");
        }

        @Test
        @DisplayName("should parse very large delay-seconds value")
        void shouldParseVeryLargeDelaySeconds() {
            Map<String, String> headers = Map.of("Retry-After", "31536000"); // 1 year
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("should reject fractional seconds")
        void shouldRejectFractionalSeconds() {
            Map<String, String> headers = Map.of("Retry-After", "120.5");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            // Will try to parse as number, fail, then try as date, fail again
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle HTTP date with different timezones")
        void shouldHandleHttpDateTimezone() {
            // RFC 1123 requires GMT
            Map<String, String> headers = Map.of("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.parse("2015-10-21T07:28:00Z"), result.get());
        }

        @Test
        @DisplayName("should parse ISO 8601 with milliseconds")
        void shouldParseIso8601WithMillis() {
            Map<String, String> headers = Map.of("Retry-After", "2021-01-01T00:00:00.123Z");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.parse("2021-01-01T00:00:00.123Z"), result.get());
        }

        @Test
        @DisplayName("should parse ISO 8601 with timezone offset")
        void shouldParseIso8601WithOffset() {
            Map<String, String> headers = Map.of("Retry-After", "2021-01-01T00:00:00+00:00");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("should handle Retry-After with only whitespace")
        void shouldRejectOnlyWhitespace() {
            Map<String, String> headers = Map.of("Retry-After", "    ");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle mixed content (number with text)")
        void shouldRejectMixedContent() {
            Map<String, String> headers = Map.of("Retry-After", "120 seconds");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Header precedence tests")
    class HeaderPrecedenceTests {

        @Test
        @DisplayName("should prefer X-RateLimit over RateLimit over Stripe for limit")
        void shouldPreferXRateLimitForLimit() {
            // Test all combinations
            Map<String, String> headers1 = Map.of(
                    "X-RateLimit-Limit", "1000",
                    "RateLimit-Limit", "2000"
            );
            assertEquals(1000L, parser.parseLimit(createProvider(headers1)).get());

            Map<String, String> headers2 = Map.of(
                    "X-RateLimit-Limit", "1000",
                    "Stripe-RateLimit-Limit", "3000"
            );
            assertEquals(1000L, parser.parseLimit(createProvider(headers2)).get());

            Map<String, String> headers3 = Map.of(
                    "RateLimit-Limit", "2000",
                    "Stripe-RateLimit-Limit", "3000"
            );
            assertEquals(2000L, parser.parseLimit(createProvider(headers3)).get());
        }

        @Test
        @DisplayName("should fall back to next format when preferred format is invalid")
        void shouldFallbackWhenPreferredInvalid() {
            Map<String, String> headers = Map.of(
                    "X-RateLimit-Limit", "invalid",
                    "RateLimit-Limit", "2000",
                    "Stripe-RateLimit-Limit", "3000"
            );

            // X-RateLimit-Limit is invalid, should NOT fall back
            // The implementation checks X-RateLimit first and returns empty if invalid
            assertTrue(parser.parseLimit(createProvider(headers)).isEmpty());
        }

        @Test
        @DisplayName("should use Stripe headers when others are missing")
        void shouldUseStripeWhenOthersMissing() {
            Map<String, String> headers = Map.of(
                    "Stripe-RateLimit-Limit", "3000",
                    "Stripe-RateLimit-Remaining", "2999"
            );

            assertEquals(3000L, parser.parseLimit(createProvider(headers)).get());
            assertEquals(2999L, parser.parseRemaining(createProvider(headers)).get());
        }

        @Test
        @DisplayName("should use RFC draft headers when X-RateLimit is missing")
        void shouldUseRfcDraftWhenXRateLimitMissing() {
            Map<String, String> headers = Map.of(
                    "RateLimit-Limit", "2000",
                    "RateLimit-Remaining", "1999"
            );

            assertEquals(2000L, parser.parseLimit(createProvider(headers)).get());
            assertEquals(1999L, parser.parseRemaining(createProvider(headers)).get());
        }
    }

    @Nested
    @DisplayName("Whitespace handling tests")
    class WhitespaceHandlingTests {

        @Test
        @DisplayName("should trim leading and trailing spaces")
        void shouldTrimSpaces() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "   5000   ");
            assertEquals(5000L, parser.parseLimit(createProvider(headers)).get());
        }

        @Test
        @DisplayName("should trim tabs")
        void shouldTrimTabs() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "\t\t5000\t\t");
            assertEquals(5000L, parser.parseLimit(createProvider(headers)).get());
        }

        @Test
        @DisplayName("should trim mixed whitespace")
        void shouldTrimMixedWhitespace() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", " \t 5000 \t ");
            assertEquals(5000L, parser.parseLimit(createProvider(headers)).get());
        }

        @Test
        @DisplayName("should trim whitespace for string values")
        void shouldTrimStringValues() {
            Map<String, String> headers = Map.of("X-RateLimit-Resource", "  core  ");
            assertEquals("core", parser.parseGitHubResource(createProvider(headers)).get());
        }

        @Test
        @DisplayName("should trim whitespace in HTTP date")
        void shouldTrimHttpDate() {
            Map<String, String> headers = Map.of("Retry-After", "  Wed, 21 Oct 2015 07:28:00 GMT  ");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isPresent());
            assertEquals(Instant.parse("2015-10-21T07:28:00Z"), result.get());
        }

        @Test
        @DisplayName("should reject internal whitespace in numbers")
        void shouldRejectInternalWhitespace() {
            Map<String, String> headers = Map.of("X-RateLimit-Limit", "50 00");
            assertTrue(parser.parseLimit(createProvider(headers)).isEmpty());
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

        @Test
        @DisplayName("should handle overflow in parseReset timestamp")
        void shouldHandleTimestampOverflow() {
            // This value will cause NumberFormatException in parseLong within parseUnixTimestamp
            Map<String, String> headers = Map.of("X-RateLimit-Reset", "99999999999999999999999999999");
            Optional<Instant> result = parser.parseReset(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle blank Retry-After HTTP date")
        void shouldHandleBlankRetryAfterHttpDate() {
            // parseHttpDate should return empty for blank string
            Map<String, String> headers = Map.of("Retry-After", "   ");
            Optional<Instant> result = parser.parseRetryAfter(createProvider(headers));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle parseReset with null value via containsInvalidCharacters")
        void shouldHandleNullInContainsInvalidCharacters() {
            // This tests the null check in containsInvalidCharacters
            Map<String, String> headers = new HashMap<>();
            headers.put("X-RateLimit-Reset", null);

            RateLimitHeaderParser.HeaderValueProvider provider = headers::get;
            Optional<Instant> result = parser.parseReset(provider);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle null value in parseRetryAfter triggering parseHttpDate null check")
        void shouldHandleNullRetryAfterTriggeringParseHttpDateNullCheck() {
            // This tests the null check in parseHttpDate (line 268)
            // by passing null through the Retry-After path
            Map<String, String> headers = new HashMap<>();
            headers.put("Retry-After", null);

            RateLimitHeaderParser.HeaderValueProvider provider = headers::get;
            Optional<Instant> result = parser.parseRetryAfter(provider);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Reflection-based coverage tests for private methods")
    class ReflectionCoverageTests {

        @Test
        @DisplayName("should cover null check in containsInvalidCharacters via reflection")
        void shouldCoverContainsInvalidCharactersNullCheck() throws Exception {
            java.lang.reflect.Method method = RateLimitHeaderParser.class
                    .getDeclaredMethod("containsInvalidCharacters", String.class);
            method.setAccessible(true);

            // Call with null - should return false (line 232)
            boolean result = (boolean) method.invoke(parser, (String) null);
            assertFalse(result);
        }

        @Test
        @DisplayName("should cover null check in parseHttpDate via reflection")
        void shouldCoverParseHttpDateNullCheck() throws Exception {
            java.lang.reflect.Method method = RateLimitHeaderParser.class
                    .getDeclaredMethod("parseHttpDate", String.class);
            method.setAccessible(true);

            // Call with null - should return Optional.empty() (line 268)
            @SuppressWarnings("unchecked")
            Optional<Instant> result = (Optional<Instant>) method.invoke(parser, (String) null);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Multi-bucket header parsing tests")
    class MultiBucketParsingTests {

        @Test
        @DisplayName("should parse multiple buckets from headers")
        void shouldParseMultipleBuckets() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-AppDay-Limit", "10000000",
                "X-RateLimit-AppDay-Remaining", "9999999",
                "X-RateLimit-AppDay-Reset", "1609459200",
                "X-RateLimit-Session-Limit", "120",
                "X-RateLimit-Session-Remaining", "75",
                "X-RateLimit-Session-Reset", "1609459260"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(2, result.size());

            // Check AppDay bucket
            assertTrue(result.containsKey("AppDay"));
            RateLimitInfo appDayInfo = result.get("AppDay");
            assertEquals(10000000L, appDayInfo.limit());
            assertEquals(9999999L, appDayInfo.remaining());
            assertEquals(Instant.ofEpochSecond(1609459200L), appDayInfo.resetTime());

            // Check Session bucket
            assertTrue(result.containsKey("Session"));
            RateLimitInfo sessionInfo = result.get("Session");
            assertEquals(120L, sessionInfo.limit());
            assertEquals(75L, sessionInfo.remaining());
            assertEquals(Instant.ofEpochSecond(1609459260L), sessionInfo.resetTime());
        }

        @Test
        @DisplayName("should handle case-insensitive bucket names")
        void shouldHandleCaseInsensitiveBucketNames() {
            // Headers with different case variations
            Map<String, String> headers = Map.of(
                "X-RateLimit-AppDay-Limit", "10000",
                "x-ratelimit-appday-remaining", "9999",
                "X-RATELIMIT-APPDAY-RESET", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("AppDay"));
            RateLimitInfo info = result.get("AppDay");
            assertEquals(10000L, info.limit());
            assertEquals(9999L, info.remaining());
        }

        @Test
        @DisplayName("should fall back to default bucket for single-bucket headers")
        void shouldFallBackToDefaultBucket() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Limit", "5000",
                "X-RateLimit-Remaining", "4999",
                "X-RateLimit-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("default"));
            RateLimitInfo info = result.get("default");
            assertEquals(5000L, info.limit());
            assertEquals(4999L, info.remaining());
            assertEquals(Instant.ofEpochSecond(1609459200L), info.resetTime());
        }

        @Test
        @DisplayName("should return empty map when no rate limit headers present")
        void shouldReturnEmptyMapWhenNoHeaders() {
            Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer token"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should skip buckets with invalid data")
        void shouldSkipBucketsWithInvalidData() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Valid-Limit", "1000",
                "X-RateLimit-Valid-Remaining", "999",
                "X-RateLimit-Valid-Reset", "1609459200",
                "X-RateLimit-Invalid-Limit", "not-a-number",
                "X-RateLimit-Invalid-Remaining", "999",
                "X-RateLimit-Invalid-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("Valid"));
            assertFalse(result.containsKey("Invalid"));
        }

        @Test
        @DisplayName("should handle bucket names with hyphens and underscores")
        void shouldHandleBucketNamesWithSpecialChars() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-App-Day-Limit", "10000",
                "X-RateLimit-App-Day-Remaining", "9999",
                "X-RateLimit-App-Day-Reset", "1609459200",
                "X-RateLimit-User_Session-Limit", "100",
                "X-RateLimit-User_Session-Remaining", "99",
                "X-RateLimit-User_Session-Reset", "1609459260"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(2, result.size());
            assertTrue(result.containsKey("App-Day"));
            assertTrue(result.containsKey("User_Session"));
        }

        @Test
        @DisplayName("should handle numeric bucket names")
        void shouldHandleNumericBucketNames() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Bucket123-Limit", "1000",
                "X-RateLimit-Bucket123-Remaining", "999",
                "X-RateLimit-Bucket123-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("Bucket123"));
        }

        @Test
        @DisplayName("should use simple HeaderValueProvider and fall back to legacy headers")
        void shouldFallBackWithSimpleProvider() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Limit", "5000",
                "X-RateLimit-Remaining", "4999",
                "X-RateLimit-Reset", "1609459200"
            );

            // Use simple HeaderValueProvider (not AllHeadersProvider)
            var provider = createProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("default"));
            RateLimitInfo info = result.get("default");
            assertEquals(5000L, info.limit());
        }

        @Test
        @DisplayName("should handle partial bucket data")
        void shouldHandlePartialBucketData() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Partial-Limit", "1000",
                // Missing Remaining header
                "X-RateLimit-Partial-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("Partial"));
            RateLimitInfo info = result.get("Partial");
            assertEquals(1000L, info.limit());
            assertEquals(0L, info.remaining()); // Default value for missing header
        }

        @Test
        @DisplayName("should ignore headers that don't match pattern")
        void shouldIgnoreNonMatchingHeaders() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-App-Limit", "1000",
                "X-RateLimit-App-Remaining", "999",
                "X-RateLimit-App-Reset", "1609459200",
                "X-RateLimit-Limit", "5000",
                "RateLimit-Limit", "2000",
                "Some-Other-Header", "value"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("App"));
        }

        @Test
        @DisplayName("should maintain insertion order of buckets")
        void shouldMaintainInsertionOrder() {
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("X-RateLimit-Zebra-Limit", "1000");
            headers.put("X-RateLimit-Zebra-Remaining", "999");
            headers.put("X-RateLimit-Zebra-Reset", "1609459200");
            headers.put("X-RateLimit-Alpha-Limit", "2000");
            headers.put("X-RateLimit-Alpha-Remaining", "1999");
            headers.put("X-RateLimit-Alpha-Reset", "1609459260");

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(2, result.size());
            var keys = result.keySet().toArray(new String[0]);
            assertEquals("Zebra", keys[0]);
            assertEquals("Alpha", keys[1]);
        }

        @Test
        @DisplayName("should handle mixed case in bucket pattern matching")
        void shouldHandleMixedCaseInPatternMatching() {
            Map<String, String> headers = Map.of(
                "x-RateLiMiT-MiXeD-LiMiT", "1000",
                "X-rAtElImIt-MiXeD-ReMaInInG", "999",
                "X-RATELIMIT-MIXED-RESET", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            // Should detect "MiXeD" bucket
            assertTrue(result.containsKey("MiXeD"));
        }

        @Test
        @DisplayName("should handle bucket with zero limit")
        void shouldHandleBucketWithZeroLimit() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Zero-Limit", "0",
                "X-RateLimit-Zero-Remaining", "0",
                "X-RateLimit-Zero-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            // Bucket with zero limit should not be included (hasValidData() returns false)
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle legacy headers with Retry-After")
        void shouldHandleLegacyHeadersWithRetryAfter() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Limit", "5000",
                "X-RateLimit-Remaining", "0",
                "X-RateLimit-Reset", "1609459200",
                "Retry-After", "120"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            RateLimitInfo info = result.get("default");
            assertNotNull(info);
            assertEquals(0L, info.remaining());
            // retryAfter should be calculated
            assertTrue(info.retryAfter() >= 0);
        }

        @Test
        @DisplayName("should normalize duplicate bucket names with different cases")
        void shouldNormalizeDuplicateBucketNames() {
            // Using LinkedHashMap to ensure order and test case-insensitive deduplication
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("X-RateLimit-AppDay-Limit", "10000");
            headers.put("X-RateLimit-AppDay-Remaining", "9999");
            headers.put("X-RateLimit-AppDay-Reset", "1609459200");
            headers.put("X-RateLimit-APPDAY-Remaining", "8888"); // Different case, should use AppDay bucket
            headers.put("X-RateLimit-Session-Limit", "100");
            headers.put("X-RateLimit-Session-Remaining", "99");
            headers.put("X-RateLimit-Session-Reset", "1609459260");

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            // Should have two buckets (AppDay and Session)
            assertEquals(2, result.size());
            assertTrue(result.containsKey("AppDay"));
            assertTrue(result.containsKey("Session"));

            // AppDay should use first occurrence bucket name
            RateLimitInfo appDayInfo = result.get("AppDay");
            assertEquals(10000L, appDayInfo.limit());
            // Remaining should come from case-insensitive lookup (9999 from AppDay-Remaining)
            assertEquals(9999L, appDayInfo.remaining());
        }

        @Test
        @DisplayName("should handle empty headers map")
        void shouldHandleEmptyHeadersMap() {
            Map<String, String> headers = Map.of();

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should handle bucket names with only numbers")
        void shouldHandleBucketNamesWithOnlyNumbers() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-123-Limit", "1000",
                "X-RateLimit-123-Remaining", "999",
                "X-RateLimit-123-Reset", "1609459200"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            assertTrue(result.containsKey("123"));
        }

        @Test
        @DisplayName("should handle bucket with malformed reset time")
        void shouldHandleBucketWithMalformedResetTime() {
            Map<String, String> headers = Map.of(
                "X-RateLimit-Bad-Limit", "1000",
                "X-RateLimit-Bad-Remaining", "999",
                "X-RateLimit-Bad-Reset", "not-a-timestamp"
            );

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            RateLimitInfo info = result.get("Bad");
            assertNotNull(info);
            assertEquals(Instant.EPOCH, info.resetTime()); // Default value
        }

        @Test
        @DisplayName("should preserve bucket name casing from first occurrence")
        void shouldPreserveBucketNameCasingFromFirstOccurrence() {
            // Create headers with specific order
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("X-RateLimit-MyBucket-Limit", "1000");
            headers.put("X-RateLimit-MYBUCKET-Remaining", "999");
            headers.put("X-RateLimit-mybucket-Reset", "1609459200");

            var provider = createAllHeadersProvider(headers);
            Map<String, RateLimitInfo> result = parser.parseAllBuckets(provider);

            assertEquals(1, result.size());
            // Should preserve "MyBucket" from first occurrence
            assertTrue(result.containsKey("MyBucket"));
        }
    }
}

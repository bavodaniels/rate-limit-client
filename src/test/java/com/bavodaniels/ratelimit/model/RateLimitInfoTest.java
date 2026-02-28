package com.bavodaniels.ratelimit.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link RateLimitInfo}.
 * Tests cover all constructors, validation, methods, and edge cases.
 */
@DisplayName("RateLimitInfo")
class RateLimitInfoTest {

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should create valid RateLimitInfo with all positive values")
        void shouldCreateValidRateLimitInfo() {
            Instant resetTime = Instant.now().plusSeconds(3600);
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 10);

            assertEquals(100, info.limit());
            assertEquals(50, info.remaining());
            assertEquals(resetTime, info.resetTime());
            assertEquals(10, info.retryAfter());
        }

        @Test
        @DisplayName("should create RateLimitInfo with zero values")
        void shouldCreateWithZeroValues() {
            RateLimitInfo info = new RateLimitInfo(0, 0, Instant.EPOCH, 0);

            assertEquals(0, info.limit());
            assertEquals(0, info.remaining());
            assertEquals(Instant.EPOCH, info.resetTime());
            assertEquals(0, info.retryAfter());
        }

        @Test
        @DisplayName("should create RateLimitInfo when remaining equals limit")
        void shouldCreateWhenRemainingEqualsLimit() {
            Instant resetTime = Instant.now();
            RateLimitInfo info = new RateLimitInfo(100, 100, resetTime, 0);

            assertEquals(100, info.limit());
            assertEquals(100, info.remaining());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is negative")
        void shouldThrowWhenLimitIsNegative() {
            Instant resetTime = Instant.now();
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitInfo(-1, 0, resetTime, 0)
            );
            assertTrue(exception.getMessage().contains("limit must be non-negative"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when remaining is negative")
        void shouldThrowWhenRemainingIsNegative() {
            Instant resetTime = Instant.now();
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitInfo(100, -1, resetTime, 0)
            );
            assertTrue(exception.getMessage().contains("remaining must be non-negative"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when remaining exceeds limit")
        void shouldThrowWhenRemainingExceedsLimit() {
            Instant resetTime = Instant.now();
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitInfo(100, 101, resetTime, 0)
            );
            assertTrue(exception.getMessage().contains("remaining"));
            assertTrue(exception.getMessage().contains("cannot exceed limit"));
        }

        @Test
        @DisplayName("should throw NullPointerException when resetTime is null")
        void shouldThrowWhenResetTimeIsNull() {
            assertThrows(
                NullPointerException.class,
                () -> new RateLimitInfo(100, 50, null, 0)
            );
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when retryAfter is negative")
        void shouldThrowWhenRetryAfterIsNegative() {
            Instant resetTime = Instant.now();
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitInfo(100, 50, resetTime, -1)
            );
            assertTrue(exception.getMessage().contains("retryAfter must be non-negative"));
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("empty() should return RateLimitInfo with default values")
        void emptyShouldReturnDefaultValues() {
            RateLimitInfo info = RateLimitInfo.empty();

            assertEquals(0, info.limit());
            assertEquals(0, info.remaining());
            assertEquals(Instant.EPOCH, info.resetTime());
            assertEquals(0, info.retryAfter());
        }

        @Test
        @DisplayName("empty() should return valid instance that doesn't throw")
        void emptyShouldReturnValidInstance() {
            assertDoesNotThrow(RateLimitInfo::empty);
            RateLimitInfo info = RateLimitInfo.empty();
            assertFalse(info.hasValidData());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build RateLimitInfo with all fields set")
        void shouldBuildWithAllFields() {
            Instant resetTime = Instant.now().plusSeconds(3600);
            RateLimitInfo info = RateLimitInfo.builder()
                .limit(100)
                .remaining(75)
                .resetTime(resetTime)
                .retryAfter(30)
                .build();

            assertEquals(100, info.limit());
            assertEquals(75, info.remaining());
            assertEquals(resetTime, info.resetTime());
            assertEquals(30, info.retryAfter());
        }

        @Test
        @DisplayName("should build RateLimitInfo with default values when no fields set")
        void shouldBuildWithDefaults() {
            RateLimitInfo info = RateLimitInfo.builder().build();

            assertEquals(0, info.limit());
            assertEquals(0, info.remaining());
            assertEquals(Instant.EPOCH, info.resetTime());
            assertEquals(0, info.retryAfter());
        }

        @Test
        @DisplayName("should build RateLimitInfo with partial fields set")
        void shouldBuildWithPartialFields() {
            RateLimitInfo info = RateLimitInfo.builder()
                .limit(50)
                .remaining(25)
                .build();

            assertEquals(50, info.limit());
            assertEquals(25, info.remaining());
            assertEquals(Instant.EPOCH, info.resetTime());
            assertEquals(0, info.retryAfter());
        }

        @Test
        @DisplayName("should allow method chaining")
        void shouldAllowMethodChaining() {
            Instant resetTime = Instant.now();
            RateLimitInfo.Builder builder = RateLimitInfo.builder();

            assertSame(builder, builder.limit(100));
            assertSame(builder, builder.remaining(50));
            assertSame(builder, builder.resetTime(resetTime));
            assertSame(builder, builder.retryAfter(10));
        }

        @Test
        @DisplayName("should throw when building with invalid values")
        void shouldThrowWhenBuildingWithInvalidValues() {
            assertThrows(
                IllegalArgumentException.class,
                () -> RateLimitInfo.builder()
                    .limit(-1)
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("isLimitExceeded()")
    class IsLimitExceeded {

        @Test
        @DisplayName("should return true when remaining is 0 and limit is positive")
        void shouldReturnTrueWhenExceeded() {
            RateLimitInfo info = new RateLimitInfo(100, 0, Instant.now(), 0);
            assertTrue(info.isLimitExceeded());
        }

        @Test
        @DisplayName("should return false when remaining is positive")
        void shouldReturnFalseWhenRemainingIsPositive() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            assertFalse(info.isLimitExceeded());
        }

        @Test
        @DisplayName("should return false when limit is 0")
        void shouldReturnFalseWhenLimitIsZero() {
            RateLimitInfo info = new RateLimitInfo(0, 0, Instant.now(), 0);
            assertFalse(info.isLimitExceeded());
        }

        @Test
        @DisplayName("should return false when remaining equals limit")
        void shouldReturnFalseWhenRemainingEqualsLimit() {
            RateLimitInfo info = new RateLimitInfo(100, 100, Instant.now(), 0);
            assertFalse(info.isLimitExceeded());
        }
    }

    @Nested
    @DisplayName("hasValidData()")
    class HasValidData {

        @Test
        @DisplayName("should return true when limit is positive")
        void shouldReturnTrueWhenLimitIsPositive() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            assertTrue(info.hasValidData());
        }

        @Test
        @DisplayName("should return false when limit is 0")
        void shouldReturnFalseWhenLimitIsZero() {
            RateLimitInfo info = new RateLimitInfo(0, 0, Instant.now(), 0);
            assertFalse(info.hasValidData());
        }

        @Test
        @DisplayName("should return false for empty instance")
        void shouldReturnFalseForEmpty() {
            assertFalse(RateLimitInfo.empty().hasValidData());
        }
    }

    @Nested
    @DisplayName("secondsUntilReset()")
    class SecondsUntilReset {

        @Test
        @DisplayName("should return positive seconds when reset is in future")
        void shouldReturnPositiveSecondsWhenResetInFuture() {
            Instant now = Instant.now();
            Instant resetTime = now.plusSeconds(3600);
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 0);

            long seconds = info.secondsUntilReset(now);
            assertEquals(3600, seconds);
        }

        @Test
        @DisplayName("should return 0 when reset time has passed")
        void shouldReturnZeroWhenResetHasPassed() {
            Instant now = Instant.now();
            Instant resetTime = now.minusSeconds(100);
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 0);

            long seconds = info.secondsUntilReset(now);
            assertEquals(0, seconds);
        }

        @Test
        @DisplayName("should return 0 when reset time equals now")
        void shouldReturnZeroWhenResetEqualsNow() {
            Instant now = Instant.now();
            RateLimitInfo info = new RateLimitInfo(100, 50, now, 0);

            long seconds = info.secondsUntilReset(now);
            assertEquals(0, seconds);
        }

        @Test
        @DisplayName("should throw NullPointerException when now is null")
        void shouldThrowWhenNowIsNull() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            assertThrows(
                NullPointerException.class,
                () -> info.secondsUntilReset(null)
            );
        }

        @Test
        @DisplayName("should calculate correctly for large time differences")
        void shouldCalculateCorrectlyForLargeTimeDifferences() {
            Instant now = Instant.now();
            Instant resetTime = now.plusSeconds(86400); // 24 hours
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 0);

            long seconds = info.secondsUntilReset(now);
            assertEquals(86400, seconds);
        }
    }

    @Nested
    @DisplayName("decrementRemaining()")
    class DecrementRemaining {

        @Test
        @DisplayName("should return new instance with remaining decremented")
        void shouldDecrementRemaining() {
            Instant resetTime = Instant.now();
            RateLimitInfo original = new RateLimitInfo(100, 50, resetTime, 10);
            RateLimitInfo decremented = original.decrementRemaining();

            assertNotSame(original, decremented);
            assertEquals(49, decremented.remaining());
            assertEquals(100, decremented.limit());
            assertEquals(resetTime, decremented.resetTime());
            assertEquals(10, decremented.retryAfter());
        }

        @Test
        @DisplayName("should return same instance when remaining is already 0")
        void shouldReturnSameInstanceWhenRemainingIsZero() {
            RateLimitInfo info = new RateLimitInfo(100, 0, Instant.now(), 0);
            RateLimitInfo decremented = info.decrementRemaining();

            assertSame(info, decremented);
            assertEquals(0, decremented.remaining());
        }

        @Test
        @DisplayName("should decrement from 1 to 0")
        void shouldDecrementFromOneToZero() {
            RateLimitInfo info = new RateLimitInfo(100, 1, Instant.now(), 0);
            RateLimitInfo decremented = info.decrementRemaining();

            assertEquals(0, decremented.remaining());
            assertTrue(decremented.isLimitExceeded());
        }

        @Test
        @DisplayName("should allow multiple decrements")
        void shouldAllowMultipleDecrements() {
            RateLimitInfo info = new RateLimitInfo(100, 3, Instant.now(), 0);

            RateLimitInfo first = info.decrementRemaining();
            assertEquals(2, first.remaining());

            RateLimitInfo second = first.decrementRemaining();
            assertEquals(1, second.remaining());

            RateLimitInfo third = second.decrementRemaining();
            assertEquals(0, third.remaining());

            RateLimitInfo fourth = third.decrementRemaining();
            assertSame(third, fourth);
            assertEquals(0, fourth.remaining());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when all fields are equal")
        void shouldBeEqualWhenFieldsAreEqual() {
            Instant resetTime = Instant.now();
            RateLimitInfo info1 = new RateLimitInfo(100, 50, resetTime, 10);
            RateLimitInfo info2 = new RateLimitInfo(100, 50, resetTime, 10);

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when limit differs")
        void shouldNotBeEqualWhenLimitDiffers() {
            Instant resetTime = Instant.now();
            RateLimitInfo info1 = new RateLimitInfo(100, 50, resetTime, 10);
            RateLimitInfo info2 = new RateLimitInfo(200, 50, resetTime, 10);

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("should not be equal when remaining differs")
        void shouldNotBeEqualWhenRemainingDiffers() {
            Instant resetTime = Instant.now();
            RateLimitInfo info1 = new RateLimitInfo(100, 50, resetTime, 10);
            RateLimitInfo info2 = new RateLimitInfo(100, 25, resetTime, 10);

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("should not be equal when resetTime differs")
        void shouldNotBeEqualWhenResetTimeDiffers() {
            Instant resetTime1 = Instant.now();
            Instant resetTime2 = resetTime1.plusSeconds(100);
            RateLimitInfo info1 = new RateLimitInfo(100, 50, resetTime1, 10);
            RateLimitInfo info2 = new RateLimitInfo(100, 50, resetTime2, 10);

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("should not be equal when retryAfter differs")
        void shouldNotBeEqualWhenRetryAfterDiffers() {
            Instant resetTime = Instant.now();
            RateLimitInfo info1 = new RateLimitInfo(100, 50, resetTime, 10);
            RateLimitInfo info2 = new RateLimitInfo(100, 50, resetTime, 20);

            assertNotEquals(info1, info2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 10);
            assertEquals(info, info);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 10);
            assertNotEquals(null, info);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 10);
            assertNotEquals("string", info);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        @DisplayName("should return non-null string representation")
        void shouldReturnNonNullString() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 10);
            assertNotNull(info.toString());
        }

        @Test
        @DisplayName("should include all field values")
        void shouldIncludeAllFieldValues() {
            Instant resetTime = Instant.parse("2024-01-01T00:00:00Z");
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 10);
            String str = info.toString();

            assertTrue(str.contains("100"));
            assertTrue(str.contains("50"));
            assertTrue(str.contains("10"));
        }

        @Test
        @DisplayName("should be consistent across calls")
        void shouldBeConsistentAcrossCalls() {
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 10);
            String str1 = info.toString();
            String str2 = info.toString();

            assertEquals(str1, str2);
        }
    }
}

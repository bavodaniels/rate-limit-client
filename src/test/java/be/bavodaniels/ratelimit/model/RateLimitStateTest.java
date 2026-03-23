package be.bavodaniels.ratelimit.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link RateLimitState}.
 * Tests cover thread-safety, state management, and all public methods.
 */
@DisplayName("RateLimitState")
class RateLimitStateTest {

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should create RateLimitState with valid host and endpoint")
        void shouldCreateWithValidHostAndEndpoint() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            assertEquals("api.example.com", state.getHost());
            assertEquals("/users", state.getEndpoint());
        }

        @Test
        @DisplayName("should initialize with empty rate limit info")
        void shouldInitializeWithEmptyInfo() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = state.getCurrentInfo();

            assertEquals(RateLimitInfo.empty(), info);
        }

        @Test
        @DisplayName("should initialize with EPOCH last updated time")
        void shouldInitializeWithEpochLastUpdated() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertEquals(Instant.EPOCH, state.getLastUpdated());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when host is null")
        void shouldThrowWhenHostIsNull() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState(null, "/users")
            );
            assertTrue(exception.getMessage().contains("host"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when host is blank")
        void shouldThrowWhenHostIsBlank() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState("   ", "/users")
            );
            assertTrue(exception.getMessage().contains("host"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when host is empty")
        void shouldThrowWhenHostIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState("", "/users")
            );
            assertTrue(exception.getMessage().contains("host"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when endpoint is null")
        void shouldThrowWhenEndpointIsNull() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState("api.example.com", null)
            );
            assertTrue(exception.getMessage().contains("endpoint"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when endpoint is blank")
        void shouldThrowWhenEndpointIsBlank() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState("api.example.com", "   ")
            );
            assertTrue(exception.getMessage().contains("endpoint"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when endpoint is empty")
        void shouldThrowWhenEndpointIsEmpty() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitState("api.example.com", "")
            );
            assertTrue(exception.getMessage().contains("endpoint"));
        }
    }

    @Nested
    @DisplayName("updateRateLimitInfo()")
    class UpdateRateLimitInfo {

        @Test
        @DisplayName("should update rate limit info")
        void shouldUpdateRateLimitInfo() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);

            state.updateRateLimitInfo(info);

            assertEquals(info, state.getCurrentInfo());
        }

        @Test
        @DisplayName("should update last updated time automatically")
        void shouldUpdateLastUpdatedTimeAutomatically() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant before = Instant.now();

            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(info);

            Instant after = Instant.now();
            Instant lastUpdated = state.getLastUpdated();

            assertFalse(lastUpdated.isBefore(before));
            assertFalse(lastUpdated.isAfter(after));
        }

        @Test
        @DisplayName("should throw NullPointerException when info is null")
        void shouldThrowWhenInfoIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertThrows(
                NullPointerException.class,
                () -> state.updateRateLimitInfo((RateLimitInfo) null)
            );
        }

        @Test
        @DisplayName("should update with specific timestamp")
        void shouldUpdateWithSpecificTimestamp() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            Instant updateTime = Instant.parse("2024-01-01T12:00:00Z");

            state.updateRateLimitInfo(info, updateTime);

            assertEquals(info, state.getCurrentInfo());
            assertEquals(updateTime, state.getLastUpdated());
        }

        @Test
        @DisplayName("should throw NullPointerException when updateTime is null")
        void shouldThrowWhenUpdateTimeIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);

            assertThrows(
                NullPointerException.class,
                () -> state.updateRateLimitInfo(info, null)
            );
        }

        @Test
        @DisplayName("should allow multiple updates")
        void shouldAllowMultipleUpdates() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            RateLimitInfo info1 = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info1);
            assertEquals(info1, state.getCurrentInfo());

            RateLimitInfo info2 = new RateLimitInfo(100, 25, Instant.now(), 0);
            state.updateRateLimitInfo(info2);
            assertEquals(info2, state.getCurrentInfo());
        }
    }

    @Nested
    @DisplayName("isLimitExceeded()")
    class IsLimitExceeded {

        @Test
        @DisplayName("should return true when limit is exceeded")
        void shouldReturnTrueWhenLimitExceeded() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 0, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            assertTrue(state.isLimitExceeded());
        }

        @Test
        @DisplayName("should return false when limit is not exceeded")
        void shouldReturnFalseWhenLimitNotExceeded() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            assertFalse(state.isLimitExceeded());
        }

        @Test
        @DisplayName("should return false for initial state")
        void shouldReturnFalseForInitialState() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertFalse(state.isLimitExceeded());
        }
    }

    @Nested
    @DisplayName("isStale()")
    class IsStale {

        @Test
        @DisplayName("should return true when reset time has passed")
        void shouldReturnTrueWhenResetTimePassed() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant resetTime = Instant.now().minusSeconds(100);
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 0);
            state.updateRateLimitInfo(info);

            assertTrue(state.isStale(Instant.now()));
        }

        @Test
        @DisplayName("should return false when reset time is in future")
        void shouldReturnFalseWhenResetTimeInFuture() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant resetTime = Instant.now().plusSeconds(3600);
            RateLimitInfo info = new RateLimitInfo(100, 50, resetTime, 0);
            state.updateRateLimitInfo(info);

            assertFalse(state.isStale(Instant.now()));
        }

        @Test
        @DisplayName("should return true when no valid data")
        void shouldReturnTrueWhenNoValidData() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertTrue(state.isStale(Instant.now()));
        }

        @Test
        @DisplayName("should throw NullPointerException when now is null")
        void shouldThrowWhenNowIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertThrows(
                NullPointerException.class,
                () -> state.isStale(null)
            );
        }
    }

    @Nested
    @DisplayName("canMakeRequest()")
    class CanMakeRequest {

        @Test
        @DisplayName("should return true when data is stale")
        void shouldReturnTrueWhenDataIsStale() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant resetTime = Instant.now().minusSeconds(100);
            RateLimitInfo info = new RateLimitInfo(100, 0, resetTime, 0);
            state.updateRateLimitInfo(info);

            assertTrue(state.canMakeRequest(Instant.now()));
        }

        @Test
        @DisplayName("should return true when no valid data")
        void shouldReturnTrueWhenNoValidData() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertTrue(state.canMakeRequest(Instant.now()));
        }

        @Test
        @DisplayName("should return true when remaining is positive")
        void shouldReturnTrueWhenRemainingIsPositive() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(info);

            assertTrue(state.canMakeRequest(Instant.now()));
        }

        @Test
        @DisplayName("should return false when limit exceeded and not stale")
        void shouldReturnFalseWhenLimitExceededAndNotStale() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 0, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(info);

            assertFalse(state.canMakeRequest(Instant.now()));
        }

        @Test
        @DisplayName("should throw NullPointerException when now is null")
        void shouldThrowWhenNowIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertThrows(
                NullPointerException.class,
                () -> state.canMakeRequest(null)
            );
        }
    }

    @Nested
    @DisplayName("getWaitTimeSeconds()")
    class GetWaitTimeSeconds {

        @Test
        @DisplayName("should return 0 when request can be made")
        void shouldReturnZeroWhenCanMakeRequest() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(info);

            assertEquals(0, state.getWaitTimeSeconds(Instant.now()));
        }

        @Test
        @DisplayName("should return retryAfter when available")
        void shouldReturnRetryAfterWhenAvailable() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 0, Instant.now().plusSeconds(3600), 300);
            state.updateRateLimitInfo(info);

            assertEquals(300, state.getWaitTimeSeconds(Instant.now()));
        }

        @Test
        @DisplayName("should return time until reset when retryAfter is 0")
        void shouldReturnTimeUntilResetWhenRetryAfterIsZero() {
            Instant now = Instant.now();
            Instant resetTime = now.plusSeconds(1800);
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 0, resetTime, 0);
            state.updateRateLimitInfo(info);

            assertEquals(1800, state.getWaitTimeSeconds(now));
        }

        @Test
        @DisplayName("should throw NullPointerException when now is null")
        void shouldThrowWhenNowIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertThrows(
                NullPointerException.class,
                () -> state.getWaitTimeSeconds(null)
            );
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        @Test
        @DisplayName("should reset to empty state")
        void shouldResetToEmptyState() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            state.reset();

            assertEquals(RateLimitInfo.empty(), state.getCurrentInfo());
            assertEquals(Instant.EPOCH, state.getLastUpdated());
        }

        @Test
        @DisplayName("should allow reset multiple times")
        void shouldAllowResetMultipleTimes() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            state.reset();
            state.reset();

            assertEquals(RateLimitInfo.empty(), state.getCurrentInfo());
        }

        @Test
        @DisplayName("should not affect host and endpoint")
        void shouldNotAffectHostAndEndpoint() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            state.reset();

            assertEquals("api.example.com", state.getHost());
            assertEquals("/users", state.getEndpoint());
        }
    }

    @Nested
    @DisplayName("snapshot()")
    class Snapshot {

        @Test
        @DisplayName("should create snapshot with current state")
        void shouldCreateSnapshotWithCurrentState() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            Instant updateTime = Instant.parse("2024-01-01T12:00:00Z");
            state.updateRateLimitInfo(info, updateTime);

            RateLimitState.StateSnapshot snapshot = state.snapshot();

            assertEquals("api.example.com", snapshot.host());
            assertEquals("/users", snapshot.endpoint());
            assertEquals(1, snapshot.buckets().size());
            assertEquals(info, snapshot.buckets().get("default"));
            assertEquals(updateTime, snapshot.lastUpdated());
        }

        @Test
        @DisplayName("should create independent snapshot")
        void shouldCreateIndependentSnapshot() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info1 = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info1);

            RateLimitState.StateSnapshot snapshot = state.snapshot();

            RateLimitInfo info2 = new RateLimitInfo(100, 25, Instant.now(), 0);
            state.updateRateLimitInfo(info2);

            assertEquals(info1, snapshot.buckets().get("default"));
            assertEquals(info2, state.getCurrentInfo());
        }

        @Test
        @DisplayName("should validate snapshot constructor")
        void shouldValidateSnapshotConstructor() {
            assertThrows(
                NullPointerException.class,
                () -> new RateLimitState.StateSnapshot(null, "/users", Map.of(), Instant.now())
            );
            assertThrows(
                NullPointerException.class,
                () -> new RateLimitState.StateSnapshot("host", null, Map.of(), Instant.now())
            );
            assertThrows(
                NullPointerException.class,
                () -> new RateLimitState.StateSnapshot("host", "/users", null, Instant.now())
            );
            assertThrows(
                NullPointerException.class,
                () -> new RateLimitState.StateSnapshot("host", "/users", Map.of(), null)
            );
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when host and endpoint are equal")
        void shouldBeEqualWhenHostAndEndpointAreEqual() {
            RateLimitState state1 = new RateLimitState("api.example.com", "/users");
            RateLimitState state2 = new RateLimitState("api.example.com", "/users");

            assertEquals(state1, state2);
            assertEquals(state1.hashCode(), state2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when host differs")
        void shouldNotBeEqualWhenHostDiffers() {
            RateLimitState state1 = new RateLimitState("api.example.com", "/users");
            RateLimitState state2 = new RateLimitState("api.other.com", "/users");

            assertNotEquals(state1, state2);
        }

        @Test
        @DisplayName("should not be equal when endpoint differs")
        void shouldNotBeEqualWhenEndpointDiffers() {
            RateLimitState state1 = new RateLimitState("api.example.com", "/users");
            RateLimitState state2 = new RateLimitState("api.example.com", "/posts");

            assertNotEquals(state1, state2);
        }

        @Test
        @DisplayName("should be equal regardless of rate limit info")
        void shouldBeEqualRegardlessOfRateLimitInfo() {
            RateLimitState state1 = new RateLimitState("api.example.com", "/users");
            RateLimitState state2 = new RateLimitState("api.example.com", "/users");

            state1.updateRateLimitInfo(new RateLimitInfo(100, 50, Instant.now(), 0));
            state2.updateRateLimitInfo(new RateLimitInfo(200, 25, Instant.now(), 10));

            assertEquals(state1, state2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertEquals(state, state);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertNotEquals(null, state);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertNotEquals("string", state);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTest {

        @Test
        @DisplayName("should return non-null string representation")
        void shouldReturnNonNullString() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            assertNotNull(state.toString());
        }

        @Test
        @DisplayName("should include host and endpoint")
        void shouldIncludeHostAndEndpoint() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            String str = state.toString();

            assertTrue(str.contains("api.example.com"));
            assertTrue(str.contains("/users"));
        }

        @Test
        @DisplayName("should include current info")
        void shouldIncludeCurrentInfo() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            String str = state.toString();
            assertTrue(str.contains("100"));
            assertTrue(str.contains("50"));
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @RepeatedTest(5)
        @DisplayName("should handle concurrent reads safely")
        void shouldHandleConcurrentReadsSafely() throws InterruptedException {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(info);

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        RateLimitInfo readInfo = state.getCurrentInfo();
                        if (readInfo.equals(info)) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            assertTrue(endLatch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount, successCount.get());
        }

        @RepeatedTest(5)
        @DisplayName("should handle concurrent writes safely")
        void shouldHandleConcurrentWritesSafely() throws InterruptedException {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        startLatch.await();
                        RateLimitInfo info = new RateLimitInfo(100, index, Instant.now(), 0);
                        state.updateRateLimitInfo(info);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            assertTrue(endLatch.await(5, TimeUnit.SECONDS));

            // Should have some valid info after all writes
            RateLimitInfo finalInfo = state.getCurrentInfo();
            assertNotNull(finalInfo);
            assertEquals(100, finalInfo.limit());
        }

        @RepeatedTest(5)
        @DisplayName("should handle concurrent mixed operations safely")
        void shouldHandleConcurrentMixedOperationsSafely() throws InterruptedException {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo initialInfo = new RateLimitInfo(100, 50, Instant.now().plusSeconds(3600), 0);
            state.updateRateLimitInfo(initialInfo);

            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Throwable> errors = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        if (index % 4 == 0) {
                            state.getCurrentInfo();
                        } else if (index % 4 == 1) {
                            state.updateRateLimitInfo(new RateLimitInfo(100, index, Instant.now(), 0));
                        } else if (index % 4 == 2) {
                            state.canMakeRequest(Instant.now());
                        } else {
                            state.snapshot();
                        }
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertTrue(errors.isEmpty(), "Expected no errors but got: " + errors);
        }

        @Test
        @DisplayName("should maintain consistency during concurrent snapshot operations")
        void shouldMaintainConsistencyDuringConcurrentSnapshots() throws InterruptedException {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            RateLimitInfo info = new RateLimitInfo(100, 50, Instant.now(), 0);
            state.updateRateLimitInfo(info);

            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<RateLimitState.StateSnapshot> snapshots = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        RateLimitState.StateSnapshot snapshot = state.snapshot();
                        synchronized (snapshots) {
                            snapshots.add(snapshot);
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount, snapshots.size());

            // All snapshots should have consistent host and endpoint
            for (RateLimitState.StateSnapshot snapshot : snapshots) {
                assertEquals("api.example.com", snapshot.host());
                assertEquals("/users", snapshot.endpoint());
                assertNotNull(snapshot.info());
                assertNotNull(snapshot.lastUpdated());
            }
        }
    }

    @Nested
    @DisplayName("getRetryAfter() Tests")
    class GetRetryAfterTest {

        @Test
        @DisplayName("should return retryAfter from current info")
        void shouldReturnRetryAfter() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            // Update with rate limit info that has retryAfter
            Instant resetTime = Instant.now().plusSeconds(60);
            RateLimitInfo info = new RateLimitInfo(100, 0, resetTime, 30);
            state.updateRateLimitInfo(info);

            assertEquals(30, state.getRetryAfter());
        }

        @Test
        @DisplayName("should return 0 when no retryAfter is set")
        void shouldReturnZeroWhenNoRetryAfter() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            assertEquals(0, state.getRetryAfter());
        }
    }

    @Nested
    @DisplayName("Multi-Bucket Support")
    class MultiBucketSupport {

        @Test
        @DisplayName("should update multiple buckets at once")
        void shouldUpdateMultipleBuckets() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 50, now.plusSeconds(3600), 0));
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0));
            buckets.put("graphql", new RateLimitInfo(1000, 800, now.plusSeconds(3600), 0));

            state.updateRateLimitInfo(buckets);

            assertEquals(3, state.getAllBuckets().size());
            assertTrue(state.getAllBuckets().contains("search"));
            assertTrue(state.getAllBuckets().contains("core"));
            assertTrue(state.getAllBuckets().contains("graphql"));
        }

        @Test
        @DisplayName("should get bucket-specific info")
        void shouldGetBucketSpecificInfo() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            RateLimitInfo searchInfo = new RateLimitInfo(100, 50, now.plusSeconds(3600), 0);
            RateLimitInfo coreInfo = new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0);

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", searchInfo);
            buckets.put("core", coreInfo);

            state.updateRateLimitInfo(buckets);

            assertEquals(searchInfo, state.getBucketInfo("search"));
            assertEquals(coreInfo, state.getBucketInfo("core"));
            assertEquals(RateLimitInfo.empty(), state.getBucketInfo("nonexistent"));
        }

        @Test
        @DisplayName("canMakeRequest should return false if ANY bucket is exceeded")
        void canMakeRequestReturnsFalseIfAnyBucketExceeded() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 0, now.plusSeconds(3600), 0)); // Exceeded
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0)); // Not exceeded

            state.updateRateLimitInfo(buckets);

            assertFalse(state.canMakeRequest(now));
        }

        @Test
        @DisplayName("canMakeRequest should return true if ALL buckets allow requests")
        void canMakeRequestReturnsTrueIfAllBucketsAllow() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 50, now.plusSeconds(3600), 0));
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0));

            state.updateRateLimitInfo(buckets);

            assertTrue(state.canMakeRequest(now));
        }

        @Test
        @DisplayName("isLimitExceeded should return true if ANY bucket is exceeded")
        void isLimitExceededReturnsTrueIfAnyBucketExceeded() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 0, now.plusSeconds(3600), 0)); // Exceeded
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0)); // Not exceeded

            state.updateRateLimitInfo(buckets);

            assertTrue(state.isLimitExceeded());
        }

        @Test
        @DisplayName("getWaitTimeSeconds should return MAX wait time across all buckets")
        void getWaitTimeSecondsReturnsMaxWaitTime() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 0, now.plusSeconds(1800), 0)); // 30 min wait
            buckets.put("core", new RateLimitInfo(5000, 0, now.plusSeconds(3600), 0));  // 60 min wait

            state.updateRateLimitInfo(buckets);

            assertEquals(3600, state.getWaitTimeSeconds(now));
        }

        @Test
        @DisplayName("getMostRestrictiveBucket should return bucket with longest wait time")
        void getMostRestrictiveBucketReturnsCorrectBucket() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 0, now.plusSeconds(1800), 0));
            buckets.put("core", new RateLimitInfo(5000, 0, now.plusSeconds(3600), 0)); // Most restrictive

            state.updateRateLimitInfo(buckets);

            assertEquals("core", state.getMostRestrictiveBucket());
        }

        @Test
        @DisplayName("getMostRestrictiveBucket should consider remaining ratio for non-exceeded buckets")
        void getMostRestrictiveBucketConsidersRemainingRatio() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 10, now.plusSeconds(3600), 0)); // 10% remaining
            buckets.put("core", new RateLimitInfo(5000, 2500, now.plusSeconds(3600), 0)); // 50% remaining

            state.updateRateLimitInfo(buckets);

            // search bucket is more restrictive (lower remaining percentage)
            assertEquals("search", state.getMostRestrictiveBucket());
        }

        @Test
        @DisplayName("getCurrentInfo should return most restrictive bucket")
        void getCurrentInfoReturnsMostRestrictiveBucket() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            RateLimitInfo searchInfo = new RateLimitInfo(100, 10, now.plusSeconds(3600), 0);
            RateLimitInfo coreInfo = new RateLimitInfo(5000, 2500, now.plusSeconds(3600), 0);

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", searchInfo);
            buckets.put("core", coreInfo);

            state.updateRateLimitInfo(buckets);

            // search is more restrictive (10% vs 50% remaining)
            assertEquals(searchInfo, state.getCurrentInfo());
        }

        @Test
        @DisplayName("should handle empty buckets map")
        void shouldHandleEmptyBucketsMap() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            state.updateRateLimitInfo(Map.of());

            assertEquals(0, state.getAllBuckets().size());
            assertEquals(RateLimitInfo.empty(), state.getCurrentInfo());
            assertTrue(state.canMakeRequest(Instant.now()));
        }

        @Test
        @DisplayName("should throw NullPointerException when buckets map is null")
        void shouldThrowWhenBucketsMapIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            assertThrows(
                NullPointerException.class,
                () -> state.updateRateLimitInfo((Map<String, RateLimitInfo>) null)
            );
        }

        @Test
        @DisplayName("should throw NullPointerException when bucket name is null in getBucketInfo")
        void shouldThrowWhenBucketNameIsNull() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");

            assertThrows(
                NullPointerException.class,
                () -> state.getBucketInfo(null)
            );
        }

        @Test
        @DisplayName("should prioritize exceeded buckets over non-exceeded buckets")
        void shouldPrioritizeExceededBuckets() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 0, now.plusSeconds(1800), 0)); // Exceeded
            buckets.put("core", new RateLimitInfo(5000, 10, now.plusSeconds(3600), 0)); // Not exceeded

            state.updateRateLimitInfo(buckets);

            // Exceeded bucket should be most restrictive
            assertEquals("search", state.getMostRestrictiveBucket());
        }

        @Test
        @DisplayName("snapshot should include all buckets")
        void snapshotShouldIncludeAllBuckets() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 50, now.plusSeconds(3600), 0));
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0));

            state.updateRateLimitInfo(buckets);

            RateLimitState.StateSnapshot snapshot = state.snapshot();

            assertEquals(2, snapshot.buckets().size());
            assertTrue(snapshot.buckets().containsKey("search"));
            assertTrue(snapshot.buckets().containsKey("core"));
        }

        @Test
        @DisplayName("reset should clear all buckets")
        void resetShouldClearAllBuckets() {
            RateLimitState state = new RateLimitState("api.example.com", "/users");
            Instant now = Instant.now();

            Map<String, RateLimitInfo> buckets = new HashMap<>();
            buckets.put("search", new RateLimitInfo(100, 50, now.plusSeconds(3600), 0));
            buckets.put("core", new RateLimitInfo(5000, 4500, now.plusSeconds(3600), 0));

            state.updateRateLimitInfo(buckets);
            state.reset();

            assertEquals(0, state.getAllBuckets().size());
            assertEquals(RateLimitInfo.empty(), state.getCurrentInfo());
        }
    }
}

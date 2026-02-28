package com.bavodaniels.ratelimit.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitTrackerTest {

    private RateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RateLimitTracker(Duration.ofSeconds(5));
    }

    @Test
    void testUpdateRateLimit() {
        Instant resetTime = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api.example.com", 100, 50, resetTime);

        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.limit());
        assertEquals(50, state.remaining());
        assertEquals(resetTime, state.resetTime());
    }

    @Test
    void testUpdateRateLimit_Overwrite() {
        Instant resetTime1 = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api.example.com", 100, 50, resetTime1);

        Instant resetTime2 = Instant.now().plusSeconds(120);
        tracker.updateRateLimit("api.example.com", 200, 75, resetTime2);

        RateLimitState state = tracker.getState("api.example.com");
        assertEquals(200, state.limit());
        assertEquals(75, state.remaining());
        assertEquals(resetTime2, state.resetTime());
    }

    @Test
    void testCheckAndWait_NoRateLimit() throws Exception {
        boolean result = tracker.checkAndWait("api.example.com");
        assertTrue(result);
    }

    @Test
    void testCheckAndWait_NotLimited() throws Exception {
        Instant resetTime = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api.example.com", 100, 50, resetTime);

        boolean result = tracker.checkAndWait("api.example.com");
        assertTrue(result);
    }

    @Test
    void testCheckAndWait_Expired() throws Exception {
        Instant resetTime = Instant.now().minusSeconds(1);
        tracker.updateRateLimit("api.example.com", 100, 0, resetTime);

        boolean result = tracker.checkAndWait("api.example.com");
        assertTrue(result);
    }

    @Test
    void testCheckAndWait_ShortWait() throws Exception {
        Instant resetTime = Instant.now().plus(2, ChronoUnit.SECONDS);
        tracker.updateRateLimit("api.example.com", 100, 0, resetTime);

        long start = System.currentTimeMillis();
        boolean result = tracker.checkAndWait("api.example.com");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result);
        assertTrue(elapsed >= 1500); // Should have waited ~2 seconds (accounting for some variance)
    }

    @Test
    void testCheckAndWait_ExceedsThreshold() {
        Instant resetTime = Instant.now().plusSeconds(10); // Beyond 5 second threshold
        tracker.updateRateLimit("api.example.com", 100, 0, resetTime);

        RateLimitException exception = assertThrows(RateLimitException.class, () -> {
            tracker.checkAndWait("api.example.com");
        });

        assertTrue(exception.getMessage().contains("Rate limit exceeded"));
        assertTrue(exception.getMessage().contains("api.example.com"));
    }

    @Test
    void testCleanupExpiredEntries() {
        Instant expiredReset = Instant.now().minusSeconds(1);
        Instant futureReset = Instant.now().plusSeconds(60);

        tracker.updateRateLimit("expired.example.com", 100, 0, expiredReset);
        tracker.updateRateLimit("active.example.com", 100, 50, futureReset);

        assertEquals(2, tracker.size());

        tracker.cleanupExpiredEntries();

        assertEquals(1, tracker.size());
        assertNull(tracker.getState("expired.example.com"));
        assertNotNull(tracker.getState("active.example.com"));
    }

    @Test
    void testClear() {
        Instant resetTime = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api1.example.com", 100, 50, resetTime);
        tracker.updateRateLimit("api2.example.com", 200, 75, resetTime);

        assertEquals(2, tracker.size());

        tracker.clear();

        assertEquals(0, tracker.size());
        assertNull(tracker.getState("api1.example.com"));
        assertNull(tracker.getState("api2.example.com"));
    }

    @Test
    void testBuildKey_WithEndpoint() {
        String key = RateLimitTracker.buildKey("api.example.com", "/users");
        assertEquals("api.example.com:/users", key);
    }

    @Test
    void testBuildKey_WithoutEndpoint() {
        String key = RateLimitTracker.buildKey("api.example.com", "");
        assertEquals("api.example.com", key);
    }

    @Test
    void testBuildKey_NullEndpoint() {
        String key = RateLimitTracker.buildKey("api.example.com", null);
        assertEquals("api.example.com", key);
    }

    @Test
    void testCustomWaitThreshold() {
        RateLimitTracker customTracker = new RateLimitTracker(Duration.ofMillis(100));
        Instant resetTime = Instant.now().plusMillis(200);
        customTracker.updateRateLimit("api.example.com", 100, 0, resetTime);

        assertThrows(RateLimitException.class, () -> {
            customTracker.checkAndWait("api.example.com");
        });
    }

    @Test
    void testMultipleKeys() {
        Instant resetTime = Instant.now().plusSeconds(60);

        tracker.updateRateLimit("api1.example.com", 100, 50, resetTime);
        tracker.updateRateLimit("api2.example.com", 200, 75, resetTime);
        tracker.updateRateLimit("api3.example.com:/users", 150, 25, resetTime);

        assertEquals(3, tracker.size());

        RateLimitState state1 = tracker.getState("api1.example.com");
        RateLimitState state2 = tracker.getState("api2.example.com");
        RateLimitState state3 = tracker.getState("api3.example.com:/users");

        assertEquals(100, state1.limit());
        assertEquals(200, state2.limit());
        assertEquals(150, state3.limit());
    }
}

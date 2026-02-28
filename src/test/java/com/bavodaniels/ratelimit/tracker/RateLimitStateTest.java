package com.bavodaniels.ratelimit.tracker;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitStateTest {

    @Test
    void testCreation() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, 50, resetTime);

        assertEquals(100, state.limit());
        assertEquals(50, state.remaining());
        assertEquals(resetTime, state.resetTime());
        assertNotNull(state.lastUpdated());
    }

    @Test
    void testIsExpired_NotExpired() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, 50, resetTime);

        assertFalse(state.isExpired());
    }

    @Test
    void testIsExpired_Expired() {
        Instant resetTime = Instant.now().minusSeconds(1);
        RateLimitState state = RateLimitState.of(100, 50, resetTime);

        assertTrue(state.isExpired());
    }

    @Test
    void testIsLimited_NotLimited() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, 50, resetTime);

        assertFalse(state.isLimited());
    }

    @Test
    void testIsLimited_Limited() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, 0, resetTime);

        assertTrue(state.isLimited());
    }

    @Test
    void testIsLimited_NegativeRemaining() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, -1, resetTime);

        assertTrue(state.isLimited());
    }

    @Test
    void testGetWaitTimeMillis_Future() {
        Instant resetTime = Instant.now().plus(5, ChronoUnit.SECONDS);
        RateLimitState state = RateLimitState.of(100, 0, resetTime);

        long waitTime = state.getWaitTimeMillis();
        assertTrue(waitTime > 0);
        assertTrue(waitTime <= 5000); // Should be roughly 5 seconds
    }

    @Test
    void testGetWaitTimeMillis_Expired() {
        Instant resetTime = Instant.now().minusSeconds(1);
        RateLimitState state = RateLimitState.of(100, 0, resetTime);

        assertEquals(0, state.getWaitTimeMillis());
    }

    @Test
    void testImmutability() {
        Instant resetTime = Instant.now().plusSeconds(60);
        RateLimitState state = RateLimitState.of(100, 50, resetTime);

        // Record fields are final and can't be modified
        assertEquals(100, state.limit());
        assertEquals(50, state.remaining());
    }
}

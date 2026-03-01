package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimitTrackerTest {

    private InMemoryRateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryRateLimitTracker();
    }

    @Test
    void testGetState_CreatesNewStateForNewHost() {
        String host = "api.example.com";
        RateLimitState state = tracker.getState(host);

        assertNotNull(state);
        assertEquals(host, state.getHost());
    }

    @Test
    void testGetState_ReturnsSameStateForSameHost() {
        String host = "api.example.com";
        RateLimitState state1 = tracker.getState(host);
        RateLimitState state2 = tracker.getState(host);

        assertSame(state1, state2);
    }

    @Test
    void testClearState_RemovesStateForHost() {
        String host = "api.example.com";

        // Create a state for the host
        RateLimitState originalState = tracker.getState(host);
        assertNotNull(originalState);

        // Clear the state
        tracker.clearState(host);

        // Getting the state again should create a new instance
        RateLimitState newState = tracker.getState(host);
        assertNotNull(newState);
        assertNotSame(originalState, newState);
    }

    @Test
    void testClearAll_RemovesAllStates() {
        String host1 = "api.example.com";
        String host2 = "api.another.com";

        // Create states for multiple hosts
        RateLimitState state1 = tracker.getState(host1);
        RateLimitState state2 = tracker.getState(host2);
        assertNotNull(state1);
        assertNotNull(state2);

        // Clear all states
        tracker.clearAll();

        // Getting the states again should create new instances
        RateLimitState newState1 = tracker.getState(host1);
        RateLimitState newState2 = tracker.getState(host2);
        assertNotSame(state1, newState1);
        assertNotSame(state2, newState2);
    }

    @Test
    void testUpdateFromHeaders_WithAllHeaders() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "50");
        headers.set("X-RateLimit-Reset", "1704067200"); // Unix timestamp

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    @Test
    void testUpdateFromHeaders_WithNoHeaders() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Should not throw exception when no rate limit headers present
        assertDoesNotThrow(() -> tracker.updateFromHeaders(host, headers));
    }

    @Test
    void testUpdateFromHeaders_WithPartialHeaders() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", "100");
        // No remaining or reset headers

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);
        assertEquals(100, state.getLimit());
    }
}

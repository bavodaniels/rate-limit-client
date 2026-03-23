package be.bavodaniels.ratelimit.tracker;

import be.bavodaniels.ratelimit.model.RateLimitState;
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

    @Test
    void testUpdateFromHeaders_MultiBucket_WithAllBuckets() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Multi-bucket headers for "AppDay" bucket
        headers.set("X-RateLimit-AppDay-Limit", "1000");
        headers.set("X-RateLimit-AppDay-Remaining", "750");
        headers.set("X-RateLimit-AppDay-Reset", "1704067200");

        // Multi-bucket headers for "AppHour" bucket
        headers.set("X-RateLimit-AppHour-Limit", "100");
        headers.set("X-RateLimit-AppHour-Remaining", "50");
        headers.set("X-RateLimit-AppHour-Reset", "1704063600");

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // Verify both buckets are tracked
        assertTrue(state.getAllBuckets().contains("AppDay"));
        assertTrue(state.getAllBuckets().contains("AppHour"));
        assertEquals(2, state.getAllBuckets().size());

        // Verify AppDay bucket
        assertEquals(1000, state.getBucketInfo("AppDay").limit());
        assertEquals(750, state.getBucketInfo("AppDay").remaining());

        // Verify AppHour bucket
        assertEquals(100, state.getBucketInfo("AppHour").limit());
        assertEquals(50, state.getBucketInfo("AppHour").remaining());
    }

    @Test
    void testUpdateFromHeaders_MultiBucket_CaseInsensitive() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Multi-bucket headers with different casing
        headers.set("X-RateLimit-appday-Limit", "1000");
        headers.set("X-RateLimit-APPDAY-Remaining", "750");
        headers.set("X-RateLimit-AppDay-Reset", "1704067200");

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // Should be treated as single bucket despite different casing
        assertEquals(1, state.getAllBuckets().size());

        // The bucket name should be normalized to the first occurrence
        String bucketName = state.getAllBuckets().iterator().next();
        assertEquals(1000, state.getBucketInfo(bucketName).limit());
    }

    @Test
    void testUpdateFromHeaders_LegacySingleBucket_BackwardsCompatibility() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Legacy single-bucket headers (no bucket name in header)
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "50");
        headers.set("X-RateLimit-Reset", "1704067200");

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // Should be mapped to "default" bucket
        assertTrue(state.getAllBuckets().contains("default"));
        assertEquals(1, state.getAllBuckets().size());

        // Verify the data is accessible via the default bucket
        assertEquals(100, state.getBucketInfo("default").limit());
        assertEquals(50, state.getBucketInfo("default").remaining());

        // Backwards compatibility: old API methods should still work
        assertEquals(100, state.getLimit());
        assertEquals(50, state.getRemaining());
    }

    @Test
    void testUpdateFromHeaders_EmptyBucketMap_NoHeaders() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // No rate limit headers at all
        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // State should have no buckets (empty map)
        assertEquals(0, state.getAllBuckets().size());

        // Backwards compatibility: should return empty info
        assertEquals(0, state.getLimit());
        assertEquals(0, state.getRemaining());
    }

    @Test
    void testUpdateFromHeaders_MultiBucket_PartialData() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Bucket with only limit (missing remaining and reset)
        headers.set("X-RateLimit-AppDay-Limit", "1000");

        // Bucket with limit and remaining (missing reset)
        headers.set("X-RateLimit-AppHour-Limit", "100");
        headers.set("X-RateLimit-AppHour-Remaining", "50");

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // Both buckets should be present
        assertEquals(2, state.getAllBuckets().size());

        // AppDay bucket should have limit only, remaining defaults to 0
        assertEquals(1000, state.getBucketInfo("AppDay").limit());
        assertEquals(0, state.getBucketInfo("AppDay").remaining());

        // AppHour bucket should have both limit and remaining
        assertEquals(100, state.getBucketInfo("AppHour").limit());
        assertEquals(50, state.getBucketInfo("AppHour").remaining());
    }

    @Test
    void testUpdateFromHeaders_MixedSingleAndMultiBucket_PrefersMultiBucket() {
        String host = "api.example.com";
        HttpHeaders headers = new HttpHeaders();

        // Both multi-bucket and single-bucket headers present
        headers.set("X-RateLimit-AppDay-Limit", "1000");
        headers.set("X-RateLimit-AppDay-Remaining", "750");
        headers.set("X-RateLimit-AppDay-Reset", "1704067200");

        // Legacy single-bucket headers (should be ignored when multi-bucket present)
        headers.set("X-RateLimit-Limit", "100");
        headers.set("X-RateLimit-Remaining", "50");
        headers.set("X-RateLimit-Reset", "1704063600");

        tracker.updateFromHeaders(host, headers);

        RateLimitState state = tracker.getState(host);

        // Should only have the multi-bucket, not the default bucket
        assertEquals(1, state.getAllBuckets().size());
        assertTrue(state.getAllBuckets().contains("AppDay"));
        assertFalse(state.getAllBuckets().contains("default"));

        // Verify multi-bucket data
        assertEquals(1000, state.getBucketInfo("AppDay").limit());
        assertEquals(750, state.getBucketInfo("AppDay").remaining());
    }

    @Test
    void testUpdateFromHeaders_StateUpdate_ReplacesExistingBuckets() {
        String host = "api.example.com";

        // First update with AppDay bucket
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("X-RateLimit-AppDay-Limit", "1000");
        headers1.set("X-RateLimit-AppDay-Remaining", "750");
        headers1.set("X-RateLimit-AppDay-Reset", "1704067200");

        tracker.updateFromHeaders(host, headers1);
        RateLimitState state = tracker.getState(host);
        assertEquals(1, state.getAllBuckets().size());
        assertTrue(state.getAllBuckets().contains("AppDay"));

        // Second update with different bucket (AppHour)
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("X-RateLimit-AppHour-Limit", "100");
        headers2.set("X-RateLimit-AppHour-Remaining", "50");
        headers2.set("X-RateLimit-AppHour-Reset", "1704063600");

        tracker.updateFromHeaders(host, headers2);

        // State should be replaced with new buckets
        assertEquals(1, state.getAllBuckets().size());
        assertTrue(state.getAllBuckets().contains("AppHour"));
        assertFalse(state.getAllBuckets().contains("AppDay"));

        // Verify new bucket data
        assertEquals(100, state.getBucketInfo("AppHour").limit());
        assertEquals(50, state.getBucketInfo("AppHour").remaining());
    }
}

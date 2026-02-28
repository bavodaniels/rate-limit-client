package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitState;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of RateLimitTracker using a concurrent hash map.
 * Thread-safe for use across concurrent requests.
 *
 * @since 1.0.0
 */
public class InMemoryRateLimitTracker implements RateLimitTracker {

    private final ConcurrentHashMap<String, RateLimitState> states = new ConcurrentHashMap<>();

    @Override
    public RateLimitState getState(String host) {
        return states.getOrDefault(host, createDefaultState());
    }

    @Override
    public void updateFromHeaders(String host, HttpHeaders headers) {
        Integer limit = parseIntHeader(headers, "X-RateLimit-Limit");
        Integer remaining = parseIntHeader(headers, "X-RateLimit-Remaining");
        Long reset = parseLongHeader(headers, "X-RateLimit-Reset");
        Long retryAfter = parseLongHeader(headers, "Retry-After");

        if (limit == null && remaining == null && reset == null && retryAfter == null) {
            // No rate limit headers present
            return;
        }

        int limitValue = limit != null ? limit : 100; // Default limit
        int remainingValue = remaining != null ? remaining : limitValue;
        Instant resetTime = reset != null ? Instant.ofEpochSecond(reset) : Instant.now().plusSeconds(60);
        long retryAfterMillis = calculateRetryAfter(retryAfter, resetTime, remainingValue);

        RateLimitState newState = new RateLimitState(limitValue, remainingValue, resetTime, retryAfterMillis);
        states.put(host, newState);
    }

    @Override
    public void clearState(String host) {
        states.remove(host);
    }

    @Override
    public void clearAll() {
        states.clear();
    }

    private RateLimitState createDefaultState() {
        return new RateLimitState(100, 100, Instant.now().plusSeconds(60), 0);
    }

    private Integer parseIntHeader(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongHeader(HttpHeaders headers, String headerName) {
        String value = headers.getFirst(headerName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long calculateRetryAfter(Long retryAfter, Instant resetTime, int remaining) {
        // If Retry-After header is present, use it (in seconds)
        if (retryAfter != null && retryAfter > 0) {
            return retryAfter * 1000;
        }

        // If no requests remaining, calculate wait time until reset
        if (remaining <= 0 && resetTime != null) {
            long waitMillis = resetTime.toEpochMilli() - Instant.now().toEpochMilli();
            return Math.max(0, waitMillis);
        }

        return 0;
    }
}

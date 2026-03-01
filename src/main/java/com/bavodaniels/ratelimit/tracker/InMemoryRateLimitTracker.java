package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitInfo;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.parser.RateLimitHeaderParser;
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

    private static final String DEFAULT_ENDPOINT = "*";
    private final ConcurrentHashMap<String, RateLimitState> states = new ConcurrentHashMap<>();
    private final RateLimitHeaderParser parser = new RateLimitHeaderParser();

    @Override
    public RateLimitState getState(String host) {
        return states.computeIfAbsent(host, h -> new RateLimitState(h, DEFAULT_ENDPOINT));
    }

    @Override
    public void updateFromHeaders(String host, HttpHeaders headers) {
        // Create a header value provider for the parser
        RateLimitHeaderParser.HeaderValueProvider headerProvider = headers::getFirst;

        // Parse headers using the RateLimitHeaderParser
        Long limit = parser.parseLimit(headerProvider).orElse(null);
        Long remaining = parser.parseRemaining(headerProvider).orElse(null);
        Instant resetInstant = parser.parseReset(headerProvider).orElse(null);
        Instant retryAfterInstant = parser.parseRetryAfter(headerProvider).orElse(null);

        if (limit == null && remaining == null && resetInstant == null && retryAfterInstant == null) {
            // No rate limit headers present
            return;
        }

        long limitValue = limit != null ? limit : 100; // Default limit
        long remainingValue = remaining != null ? remaining : limitValue;
        Instant resetTime = resetInstant != null ? resetInstant : Instant.now().plusSeconds(60);
        long retryAfterSeconds = calculateRetryAfterSeconds(retryAfterInstant, resetTime, remainingValue);

        RateLimitInfo info = new RateLimitInfo(limitValue, remainingValue, resetTime, retryAfterSeconds);
        RateLimitState state = getState(host);
        state.updateRateLimitInfo(info);
    }

    @Override
    public void clearState(String host) {
        states.remove(host);
    }

    @Override
    public void clearAll() {
        states.clear();
    }

    private long calculateRetryAfterSeconds(Instant retryAfterTime, Instant resetTime, long remaining) {
        // If Retry-After time is present, calculate wait time in seconds
        if (retryAfterTime != null) {
            long waitSeconds = retryAfterTime.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, waitSeconds);
        }

        // If no requests remaining, calculate wait time until reset in seconds
        if (remaining <= 0 && resetTime != null) {
            long waitSeconds = resetTime.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, waitSeconds);
        }

        return 0;
    }
}

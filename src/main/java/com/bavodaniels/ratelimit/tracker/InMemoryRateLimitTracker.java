package com.bavodaniels.ratelimit.tracker;

import com.bavodaniels.ratelimit.model.RateLimitInfo;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.parser.RateLimitHeaderParser;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
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
        Map<String, RateLimitInfo> buckets = parser.parseAllBuckets(toHeadersProvider(headers));

        // If no rate limit headers found, return without updating
        if (buckets.isEmpty()) {
            return;
        }

        // Update the state with all detected buckets
        RateLimitState state = getState(host);
        state.updateRateLimitInfo(buckets);
    }

    private RateLimitHeaderParser.AllHeadersProvider toHeadersProvider(HttpHeaders headers) {
        return new RateLimitHeaderParser.AllHeadersProvider() {
            @Override
            public String getHeader(String headerName) {
                return headers.getFirst(headerName);
            }

            @Override
            public Map<String, String> getAllHeaders() {
                Map<String, String> allHeaders = new HashMap<>();
                headers.forEach((name, values) -> {
                    if (values != null && !values.isEmpty()) {
                        allHeaders.put(name, values.get(0));
                    }
                });
                return allHeaders;
            }
        };
    }

    @Override
    public void clearState(String host) {
        states.remove(host);
    }

    @Override
    public void clearAll() {
        states.clear();
    }
}

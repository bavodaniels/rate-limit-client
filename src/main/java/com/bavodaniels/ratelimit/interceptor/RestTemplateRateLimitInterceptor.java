package com.bavodaniels.ratelimit.interceptor;

import com.bavodaniels.ratelimit.exception.RateLimitExceededException;
import com.bavodaniels.ratelimit.model.RateLimitState;
import com.bavodaniels.ratelimit.tracker.RateLimitTracker;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * RestTemplate interceptor that automatically handles rate limiting for HTTP requests.
 * This interceptor checks rate limit state before each request and blocks the thread
 * if necessary. After receiving a response, it updates the rate limit state based on
 * response headers.
 *
 * <p>Thread-safe for use with concurrent requests.</p>
 *
 * @since 1.0.0
 */
public class RestTemplateRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final RateLimitTracker rateLimitTracker;
    private final long maxWaitTimeMillis;

    /**
     * Creates a new RestTemplateRateLimitInterceptor with default max wait time of 30 seconds.
     *
     * @param rateLimitTracker the tracker to use for managing rate limit state
     */
    public RestTemplateRateLimitInterceptor(RateLimitTracker rateLimitTracker) {
        this(rateLimitTracker, 30000); // Default 30 seconds
    }

    /**
     * Creates a new RestTemplateRateLimitInterceptor with a custom max wait time.
     *
     * @param rateLimitTracker the tracker to use for managing rate limit state
     * @param maxWaitTimeMillis the maximum time in milliseconds to wait before throwing an exception
     */
    public RestTemplateRateLimitInterceptor(RateLimitTracker rateLimitTracker, long maxWaitTimeMillis) {
        if (rateLimitTracker == null) {
            throw new IllegalArgumentException("RateLimitTracker cannot be null");
        }
        if (maxWaitTimeMillis < 0) {
            throw new IllegalArgumentException("Max wait time cannot be negative");
        }
        this.rateLimitTracker = rateLimitTracker;
        this.maxWaitTimeMillis = maxWaitTimeMillis;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        String host = extractHost(request.getURI());

        // Pre-request: Check rate limit state and handle blocking
        handlePreRequest(host);

        ClientHttpResponse response = null;
        try {
            // Execute the request
            response = execution.execute(request, body);

            // Post-response: Update rate limit state from headers
            handlePostResponse(host, response);

            return response;
        } catch (IOException e) {
            // Ensure we don't leak the response on error
            if (response != null) {
                try {
                    response.close();
                } catch (Exception closeException) {
                    // Suppress close exception, throw original
                }
            }
            throw e;
        }
    }

    /**
     * Handles pre-request rate limit checking.
     * Checks the current rate limit state and blocks the thread if necessary.
     *
     * @param host the host to check
     * @throws RateLimitExceededException if the required wait time exceeds the threshold
     */
    private void handlePreRequest(String host) {
        RateLimitState state = rateLimitTracker.getState(host);

        if (state.requiresWait()) {
            long waitTime = state.getRetryAfterMillis();

            // Check if wait time exceeds threshold
            if (waitTime > maxWaitTimeMillis) {
                throw new RateLimitExceededException(host, waitTime, maxWaitTimeMillis);
            }

            // Block the thread for the required wait time
            if (waitTime > 0) {
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    // Restore interrupt status
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting for rate limit", e);
                }
            }
        }
    }

    /**
     * Handles post-response processing.
     * Updates the rate limit state based on response headers.
     *
     * @param host the host to update
     * @param response the HTTP response containing rate limit headers
     */
    private void handlePostResponse(String host, ClientHttpResponse response) {
        try {
            rateLimitTracker.updateFromHeaders(host, response.getHeaders());
        } catch (Exception e) {
            // Log error but don't fail the request
            // In production, this would use a logger
            System.err.println("Failed to update rate limit state for host " + host + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the host from a URI.
     *
     * @param uri the URI to extract from
     * @return the host (hostname:port or just hostname)
     */
    private String extractHost(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();

        if (port != -1 && port != 80 && port != 443) {
            return host + ":" + port;
        }

        return host;
    }

    /**
     * Gets the rate limit tracker used by this interceptor.
     *
     * @return the rate limit tracker
     */
    public RateLimitTracker getRateLimitTracker() {
        return rateLimitTracker;
    }

    /**
     * Gets the maximum wait time in milliseconds.
     *
     * @return the max wait time
     */
    public long getMaxWaitTimeMillis() {
        return maxWaitTimeMillis;
    }
}

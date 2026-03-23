package be.bavodaniels.ratelimit.tracker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitExceptionTest {

    @Test
    void testExceptionWithMessage() {
        String message = "Rate limit exceeded";
        RateLimitException exception = new RateLimitException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionWithMessageAndCause() {
        String message = "Rate limit exceeded";
        Throwable cause = new RuntimeException("Original cause");
        RateLimitException exception = new RateLimitException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}

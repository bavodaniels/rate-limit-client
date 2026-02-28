package com.bavodaniels.ratelimit.exception;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RateLimitExceededException}.
 */
class RateLimitExceededExceptionTest {

    @Test
    void testExceptionCreationWithDefaultMessage() {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/users";
        Instant retryAfter = Instant.parse("2026-02-28T12:00:00Z");
        Duration waitDuration = Duration.ofSeconds(60);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertNotNull(exception);
        assertEquals(host, exception.getHost());
        assertEquals(endpoint, exception.getEndpoint());
        assertEquals(retryAfter, exception.getRetryAfter());
        assertEquals(waitDuration, exception.getWaitDuration());

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(host), "Message should contain host");
        assertTrue(message.contains(endpoint), "Message should contain endpoint");
        assertTrue(message.contains("60 seconds"), "Message should contain wait duration");
        assertTrue(message.contains("Retry after"), "Message should contain retry-after information");
    }

    @Test
    void testExceptionCreationWithCustomMessage() {
        // Given
        String customMessage = "Custom error message for rate limiting";
        String host = "api.example.com";
        String endpoint = "/api/products";
        Instant retryAfter = Instant.parse("2026-02-28T13:30:00Z");
        Duration waitDuration = Duration.ofMinutes(5);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(
                customMessage, host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals(customMessage, exception.getMessage());
        assertEquals(host, exception.getHost());
        assertEquals(endpoint, exception.getEndpoint());
        assertEquals(retryAfter, exception.getRetryAfter());
        assertEquals(waitDuration, exception.getWaitDuration());
    }

    @Test
    void testGettersReturnCorrectValues() {
        // Given
        String host = "api.github.com";
        String endpoint = "/repos/owner/repo";
        Instant retryAfter = Instant.now().plusSeconds(120);
        Duration waitDuration = Duration.ofSeconds(120);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals(host, exception.getHost(), "getHost() should return correct value");
        assertEquals(endpoint, exception.getEndpoint(), "getEndpoint() should return correct value");
        assertEquals(retryAfter, exception.getRetryAfter(), "getRetryAfter() should return correct value");
        assertEquals(waitDuration, exception.getWaitDuration(), "getWaitDuration() should return correct value");
    }

    @Test
    void testExceptionMessageIsActionable() {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/data";
        Instant retryAfter = Instant.parse("2026-02-28T14:00:00Z");
        Duration waitDuration = Duration.ofSeconds(30);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        String message = exception.getMessage();
        assertTrue(message.contains("Rate limit exceeded"), "Message should indicate rate limit exceeded");
        assertTrue(message.contains("Retry after") || message.contains("retry"),
                   "Message should provide retry information");
        assertTrue(message.toLowerCase().contains("backoff") || message.toLowerCase().contains("reduce"),
                   "Message should provide actionable guidance");
    }

    @Test
    void testExceptionWithZeroWaitDuration() {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/health";
        Instant retryAfter = Instant.now();
        Duration waitDuration = Duration.ZERO;

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals(Duration.ZERO, exception.getWaitDuration());
        String message = exception.getMessage();
        assertTrue(message.contains("0 seconds"), "Message should show 0 seconds wait");
    }

    @Test
    void testExceptionWithLongWaitDuration() {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/batch";
        Instant retryAfter = Instant.now().plus(Duration.ofHours(2));
        Duration waitDuration = Duration.ofHours(2);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals(Duration.ofHours(2), exception.getWaitDuration());
        String message = exception.getMessage();
        assertTrue(message.contains("7200 seconds"), "Message should show wait duration in seconds");
    }

    @Test
    void testExceptionIsRuntimeException() {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/test";
        Instant retryAfter = Instant.now();
        Duration waitDuration = Duration.ofSeconds(10);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertTrue(exception instanceof RuntimeException, "Exception should extend RuntimeException");
    }

    @Test
    void testSerializationAndDeserialization() throws Exception {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/users";
        Instant retryAfter = Instant.parse("2026-02-28T15:00:00Z");
        Duration waitDuration = Duration.ofSeconds(90);
        RateLimitExceededException original = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // When - Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        byte[] serializedData = baos.toByteArray();

        // When - Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
        RateLimitExceededException deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (RateLimitExceededException) ois.readObject();
        }

        // Then
        assertNotNull(deserialized);
        assertEquals(original.getHost(), deserialized.getHost());
        assertEquals(original.getEndpoint(), deserialized.getEndpoint());
        assertEquals(original.getRetryAfter(), deserialized.getRetryAfter());
        assertEquals(original.getWaitDuration(), deserialized.getWaitDuration());
        assertEquals(original.getMessage(), deserialized.getMessage());
    }

    @Test
    void testSerializationWithCustomMessage() throws Exception {
        // Given
        String customMessage = "Custom serialization test message";
        String host = "api.example.com";
        String endpoint = "/api/products";
        Instant retryAfter = Instant.parse("2026-02-28T16:00:00Z");
        Duration waitDuration = Duration.ofMinutes(2);
        RateLimitExceededException original = new RateLimitExceededException(
                customMessage, host, endpoint, retryAfter, waitDuration);

        // When - Serialize and Deserialize
        byte[] serializedData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            serializedData = baos.toByteArray();
        }

        RateLimitExceededException deserialized;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = (RateLimitExceededException) ois.readObject();
        }

        // Then
        assertEquals(customMessage, deserialized.getMessage());
        assertEquals(original.getHost(), deserialized.getHost());
        assertEquals(original.getEndpoint(), deserialized.getEndpoint());
        assertEquals(original.getRetryAfter(), deserialized.getRetryAfter());
        assertEquals(original.getWaitDuration(), deserialized.getWaitDuration());
    }

    @Test
    void testSerializationPreservesExceptionType() throws Exception {
        // Given
        String host = "api.example.com";
        String endpoint = "/api/data";
        Instant retryAfter = Instant.now();
        Duration waitDuration = Duration.ofSeconds(45);
        RateLimitExceededException original = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // When
        byte[] serializedData;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
            serializedData = baos.toByteArray();
        }

        Object deserialized;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserialized = ois.readObject();
        }

        // Then
        assertInstanceOf(RateLimitExceededException.class, deserialized);
        assertInstanceOf(RuntimeException.class, deserialized);
    }

    @Test
    void testExceptionWithRootSlashEndpoint() {
        // Given
        String host = "api.example.com";
        String endpoint = "/";
        Instant retryAfter = Instant.now();
        Duration waitDuration = Duration.ofSeconds(15);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals("/", exception.getEndpoint());
        String message = exception.getMessage();
        assertTrue(message.contains(host + endpoint) || message.contains(host + "/" ));
    }

    @Test
    void testExceptionWithEmptyEndpoint() {
        // Given
        String host = "api.example.com";
        String endpoint = "";
        Instant retryAfter = Instant.now();
        Duration waitDuration = Duration.ofSeconds(20);

        // When
        RateLimitExceededException exception = new RateLimitExceededException(host, endpoint, retryAfter, waitDuration);

        // Then
        assertEquals("", exception.getEndpoint());
        assertNotNull(exception.getMessage());
    }

    @Test
    void testExceptionMessageFormatWithVariousInputs() {
        // Test case 1: Standard API endpoint
        RateLimitExceededException ex1 = new RateLimitExceededException(
                "https://api.example.com", "/api/v1/users",
                Instant.parse("2026-02-28T12:00:00Z"), Duration.ofSeconds(60));
        assertTrue(ex1.getMessage().contains("https://api.example.com/api/v1/users"));

        // Test case 2: Short wait time
        RateLimitExceededException ex2 = new RateLimitExceededException(
                "api.github.com", "/repos",
                Instant.parse("2026-02-28T12:00:05Z"), Duration.ofSeconds(5));
        assertTrue(ex2.getMessage().contains("5 seconds"));

        // Test case 3: Long wait time
        RateLimitExceededException ex3 = new RateLimitExceededException(
                "api.service.io", "/endpoint",
                Instant.parse("2026-02-28T13:00:00Z"), Duration.ofMinutes(30));
        assertTrue(ex3.getMessage().contains("1800 seconds"));
    }
}

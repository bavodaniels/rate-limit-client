package com.bavodaniels.ratelimit.tracker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread-safety tests for RateLimitTracker with 100+ concurrent threads.
 * Tests concurrent reads, writes, and mixed operations.
 */
class RateLimitTrackerConcurrencyTest {

    private RateLimitTracker tracker;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        tracker = new RateLimitTracker(Duration.ofSeconds(5));
        executorService = Executors.newFixedThreadPool(150);
    }

    @Test
    void testConcurrentUpdates_100Threads() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        Instant resetTime = Instant.now().plusSeconds(60);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    tracker.updateRateLimit("api.example.com", 100, threadId, resetTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // Verify tracker has the entry and is in a valid state
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.limit());
        // The remaining value will be from one of the threads (0-99)
        assertTrue(state.remaining() >= 0 && state.remaining() < threadCount);
    }

    @Test
    void testConcurrentReads_200Threads() throws InterruptedException {
        Instant resetTime = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api.example.com", 100, 50, resetTime);

        int threadCount = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    RateLimitState state = tracker.getState("api.example.com");
                    if (state != null && state.limit() == 100 && state.remaining() == 50) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // All threads should have read the same consistent state
        assertEquals(threadCount, successCount.get());
    }

    @Test
    void testConcurrentCheckAndWait_150Threads_NotLimited() throws InterruptedException {
        Instant resetTime = Instant.now().plusSeconds(60);
        tracker.updateRateLimit("api.example.com", 100, 50, resetTime);

        int threadCount = 150;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    boolean result = tracker.checkAndWait("api.example.com");
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (RateLimitException e) {
                    exceptionCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // All threads should succeed since not rate limited
        assertEquals(threadCount, successCount.get());
        assertEquals(0, exceptionCount.get());
    }

    @Test
    void testConcurrentMixedOperations_300Threads() throws InterruptedException {
        int threadCount = 300;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger checkCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // Different operations based on thread ID
                    if (threadId % 3 == 0) {
                        // Update operation
                        Instant resetTime = Instant.now().plusSeconds(60);
                        tracker.updateRateLimit("api" + (threadId % 10) + ".example.com",
                            100, 50, resetTime);
                        updateCount.incrementAndGet();
                    } else if (threadId % 3 == 1) {
                        // Read operation
                        tracker.getState("api" + (threadId % 10) + ".example.com");
                        readCount.incrementAndGet();
                    } else {
                        // Check operation
                        try {
                            tracker.checkAndWait("api" + (threadId % 10) + ".example.com");
                        } catch (RateLimitException e) {
                            // Expected in some cases
                        }
                        checkCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(15, TimeUnit.SECONDS));

        // Verify all operations completed
        assertEquals(100, updateCount.get());
        assertEquals(100, readCount.get());
        assertEquals(100, checkCount.get());

        // Verify tracker is in a valid state
        assertTrue(tracker.size() <= 10); // At most 10 different keys
    }

    @Test
    void testConcurrentUpdatesMultipleKeys_200Threads() throws InterruptedException {
        int threadCount = 200;
        int keyCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String key = "api" + (threadId % keyCount) + ".example.com";
                    Instant resetTime = Instant.now().plusSeconds(60);
                    tracker.updateRateLimit(key, 100, 50, resetTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // Verify we have exactly keyCount entries
        assertEquals(keyCount, tracker.size());

        // Verify all keys are present and have valid state
        for (int i = 0; i < keyCount; i++) {
            String key = "api" + i + ".example.com";
            RateLimitState state = tracker.getState(key);
            assertNotNull(state);
            assertEquals(100, state.limit());
            assertEquals(50, state.remaining());
        }
    }

    @Test
    void testConcurrentCleanup_100Threads() throws InterruptedException {
        // Add expired and non-expired entries
        Instant expiredReset = Instant.now().minusSeconds(1);
        Instant futureReset = Instant.now().plusSeconds(60);

        for (int i = 0; i < 50; i++) {
            tracker.updateRateLimit("expired" + i + ".example.com", 100, 0, expiredReset);
            tracker.updateRateLimit("active" + i + ".example.com", 100, 50, futureReset);
        }

        assertEquals(100, tracker.size());

        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // Multiple threads calling cleanup concurrently
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    tracker.cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // Only active entries should remain
        assertEquals(50, tracker.size());

        // Verify expired entries are gone
        for (int i = 0; i < 50; i++) {
            assertNull(tracker.getState("expired" + i + ".example.com"));
            assertNotNull(tracker.getState("active" + i + ".example.com"));
        }
    }

    @Test
    void testAtomicUpdateBehavior() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        // All threads update the same key with different remaining values
        for (int i = 0; i < threadCount; i++) {
            final int remaining = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    Instant resetTime = Instant.now().plusSeconds(60);
                    tracker.updateRateLimit("api.example.com", 100, remaining, resetTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));

        // The final state should be from one of the threads (atomic operation)
        RateLimitState state = tracker.getState("api.example.com");
        assertNotNull(state);
        assertEquals(100, state.limit());
        assertTrue(state.remaining() >= 0 && state.remaining() < threadCount);
    }

    @Test
    void testNoDataCorruption_StressTest() throws InterruptedException {
        int threadCount = 500;
        int iterations = 10;
        CountDownLatch endLatch = new CountDownLatch(threadCount * iterations);

        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                final int iterationId = iter;
                executorService.submit(() -> {
                    try {
                        String key = "api" + (threadId % 50) + ".example.com";
                        Instant resetTime = Instant.now().plusSeconds(60);

                        // Random operations
                        if (threadId % 4 == 0) {
                            tracker.updateRateLimit(key, 100, 50, resetTime);
                        } else if (threadId % 4 == 1) {
                            tracker.getState(key);
                        } else if (threadId % 4 == 2) {
                            try {
                                tracker.checkAndWait(key);
                            } catch (RateLimitException e) {
                                // Expected
                            }
                        } else {
                            if (iterationId % 2 == 0) {
                                tracker.cleanupExpiredEntries();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        assertTrue(endLatch.await(30, TimeUnit.SECONDS));

        // Verify tracker is in a consistent state
        assertTrue(tracker.size() <= 50);

        // All entries should have valid state
        for (int i = 0; i < 50; i++) {
            String key = "api" + i + ".example.com";
            RateLimitState state = tracker.getState(key);
            if (state != null) {
                assertTrue(state.limit() > 0);
                assertTrue(state.remaining() >= 0);
                assertNotNull(state.resetTime());
                assertNotNull(state.lastUpdated());
            }
        }
    }
}

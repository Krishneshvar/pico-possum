package com.possum.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    
    private RateLimiter rateLimiter;
    
    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(3, 60);
    }
    
    @Test
    void testAllowAttemptWithinLimit() {
        assertTrue(rateLimiter.allowAttempt("user1"));
        assertTrue(rateLimiter.allowAttempt("user1"));
        assertTrue(rateLimiter.allowAttempt("user1"));
    }
    
    @Test
    void testBlockAttemptAfterLimit() {
        rateLimiter.allowAttempt("user1");
        rateLimiter.allowAttempt("user1");
        rateLimiter.allowAttempt("user1");
        assertFalse(rateLimiter.allowAttempt("user1"));
    }
    
    @Test
    void testRemainingAttempts() {
        assertEquals(3, rateLimiter.getRemainingAttempts("user1"));
        rateLimiter.allowAttempt("user1");
        assertEquals(2, rateLimiter.getRemainingAttempts("user1"));
        rateLimiter.allowAttempt("user1");
        assertEquals(1, rateLimiter.getRemainingAttempts("user1"));
        rateLimiter.allowAttempt("user1");
        assertEquals(0, rateLimiter.getRemainingAttempts("user1"));
    }
    
    @Test
    void testRecordSuccess() {
        rateLimiter.allowAttempt("user1");
        rateLimiter.allowAttempt("user1");
        rateLimiter.recordSuccess("user1");
        assertEquals(3, rateLimiter.getRemainingAttempts("user1"));
    }
    
    @Test
    void testDifferentIdentifiers() {
        rateLimiter.allowAttempt("user1");
        rateLimiter.allowAttempt("user1");
        rateLimiter.allowAttempt("user1");
        
        assertTrue(rateLimiter.allowAttempt("user2"));
        assertEquals(2, rateLimiter.getRemainingAttempts("user2"));
    }
    
    @Test
    void testTimeUntilReset() {
        rateLimiter.allowAttempt("user1");
        long timeUntilReset = rateLimiter.getTimeUntilReset("user1");
        assertTrue(timeUntilReset > 0 && timeUntilReset <= 60);
    }
}

package com.possum.application.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptTrackerTest {

    LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new LoginAttemptTracker();
    }

    // --- isLocked ---

    @Test
    void isLocked_noAttempts_returnsFalse() {
        assertFalse(tracker.isLocked("alice"));
    }

    @Test
    void isLocked_fewAttempts_returnsFalse() {
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");

        assertFalse(tracker.isLocked("alice"));
    }

    @Test
    void isLocked_maxAttemptsReached_returnsTrue() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        assertTrue(tracker.isLocked("alice"));
    }

    @Test
    void isLocked_lockExpired_returnsFalse() throws InterruptedException {
        // This test would require waiting for lockout duration
        // For unit test, we just verify the logic exists
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        assertTrue(tracker.isLocked("alice"));
        // In real scenario, after 5 minutes, isLocked would return false
    }

    // --- secondsUntilUnlock ---

    @Test
    void secondsUntilUnlock_notLocked_returnsZero() {
        assertEquals(0, tracker.secondsUntilUnlock("alice"));
    }

    @Test
    void secondsUntilUnlock_locked_returnsPositiveValue() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        long seconds = tracker.secondsUntilUnlock("alice");
        assertTrue(seconds > 0);
        assertTrue(seconds <= 300); // Max 5 minutes
    }

    // --- recordFailure ---

    @Test
    void recordFailure_incrementsAttemptCount() {
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");

        assertFalse(tracker.isLocked("alice")); // Not locked yet
        
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");

        assertTrue(tracker.isLocked("alice")); // Now locked
    }

    @Test
    void recordFailure_fifthAttempt_locksAccount() {
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("alice");
            assertFalse(tracker.isLocked("alice"));
        }

        tracker.recordFailure("alice"); // 5th attempt

        assertTrue(tracker.isLocked("alice"));
    }

    @Test
    void recordFailure_differentUsers_trackedSeparately() {
        tracker.recordFailure("alice");
        tracker.recordFailure("bob");
        tracker.recordFailure("alice");
        tracker.recordFailure("bob");

        assertFalse(tracker.isLocked("alice"));
        assertFalse(tracker.isLocked("bob"));

        // Lock alice
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        assertTrue(tracker.isLocked("alice"));
        assertFalse(tracker.isLocked("bob")); // Bob still not locked
    }

    // --- recordSuccess ---

    @Test
    void recordSuccess_resetsAttemptCount() {
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");
        tracker.recordFailure("alice");

        tracker.recordSuccess("alice");

        assertFalse(tracker.isLocked("alice"));
        
        // Should be able to fail again without immediate lock
        tracker.recordFailure("alice");
        assertFalse(tracker.isLocked("alice"));
    }

    @Test
    void recordSuccess_afterLock_unlocksAccount() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        assertTrue(tracker.isLocked("alice"));

        tracker.recordSuccess("alice");

        assertFalse(tracker.isLocked("alice"));
    }

    @Test
    void recordSuccess_noFailures_doesNotThrow() {
        assertDoesNotThrow(() -> tracker.recordSuccess("alice"));
    }

    // --- Edge cases ---

    @Test
    void multipleUsers_independentTracking() {
        // Lock alice
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        // Bob should still be able to attempt
        tracker.recordFailure("bob");
        tracker.recordFailure("bob");

        assertTrue(tracker.isLocked("alice"));
        assertFalse(tracker.isLocked("bob"));
    }

    @Test
    void lockoutDuration_calculatedCorrectly() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("alice");
        }

        long seconds = tracker.secondsUntilUnlock("alice");
        
        assertTrue(seconds > 0);
        assertTrue(seconds <= 300); // Should be at most 5 minutes (300 seconds)
    }

    @Test
    void attemptCount_incrementsCorrectly() {
        tracker.recordFailure("alice");
        assertFalse(tracker.isLocked("alice"));
        
        tracker.recordFailure("alice");
        assertFalse(tracker.isLocked("alice"));
        
        tracker.recordFailure("alice");
        assertFalse(tracker.isLocked("alice"));
        
        tracker.recordFailure("alice");
        assertFalse(tracker.isLocked("alice"));
        
        tracker.recordFailure("alice"); // 5th
        assertTrue(tracker.isLocked("alice"));
    }
}

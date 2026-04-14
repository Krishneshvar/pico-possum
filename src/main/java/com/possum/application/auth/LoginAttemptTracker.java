package com.possum.application.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public final class LoginAttemptTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptTracker.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 5 * 60;

    private record AttemptRecord(int count, long lockedUntil) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        AttemptRecord record = attempts.get(username);
        if (record == null) return false;
        if (record.lockedUntil() > 0 && now() < record.lockedUntil()) return true;
        if (record.lockedUntil() > 0 && now() >= record.lockedUntil()) {
            attempts.remove(username);
        }
        return false;
    }

    public long secondsUntilUnlock(String username) {
        AttemptRecord record = attempts.get(username);
        if (record == null || record.lockedUntil() == 0) return 0;
        long remaining = record.lockedUntil() - now();
        return Math.max(0, remaining);
    }

    public void recordFailure(String username) {
        AttemptRecord current = attempts.getOrDefault(username, new AttemptRecord(0, 0));
        int newCount = current.count() + 1;
        long lockedUntil = newCount >= MAX_ATTEMPTS ? now() + LOCKOUT_DURATION_SECONDS : 0;
        attempts.put(username, new AttemptRecord(newCount, lockedUntil));
        if (lockedUntil > 0) {
            LOGGER.warn("LOGIN_LOCKED username={} after {} failed attempts", username, newCount);
        } else {
            LOGGER.warn("LOGIN_FAILED username={} attempt={}/{}", username, newCount, MAX_ATTEMPTS);
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    private long now() {
        return System.currentTimeMillis() / 1000;
    }
}

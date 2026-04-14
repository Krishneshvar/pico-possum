package com.possum.infrastructure.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {
    
    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowSeconds;
    
    public RateLimiter(int maxAttempts, long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }
    
    public boolean allowAttempt(String identifier) {
        AttemptRecord record = attempts.computeIfAbsent(identifier, k -> new AttemptRecord());
        
        long now = Instant.now().getEpochSecond();
        
        if (now - record.windowStart > windowSeconds) {
            record.reset(now);
        }
        
        if (record.count.get() >= maxAttempts) {
            return false;
        }
        
        record.count.incrementAndGet();
        return true;
    }
    
    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }
    
    public int getRemainingAttempts(String identifier) {
        AttemptRecord record = attempts.get(identifier);
        if (record == null) {
            return maxAttempts;
        }
        
        long now = Instant.now().getEpochSecond();
        if (now - record.windowStart > windowSeconds) {
            return maxAttempts;
        }
        
        return Math.max(0, maxAttempts - record.count.get());
    }
    
    public long getTimeUntilReset(String identifier) {
        AttemptRecord record = attempts.get(identifier);
        if (record == null) {
            return 0;
        }
        
        long now = Instant.now().getEpochSecond();
        long elapsed = now - record.windowStart;
        
        if (elapsed > windowSeconds) {
            return 0;
        }
        
        return windowSeconds - elapsed;
    }
    
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> 
            now - entry.getValue().windowStart > windowSeconds
        );
    }
    
    private static class AttemptRecord {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().getEpochSecond();
        
        void reset(long newStart) {
            count.set(0);
            windowStart = newStart;
        }
    }
}

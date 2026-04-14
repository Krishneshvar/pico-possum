package com.possum.application.auth;

import com.possum.infrastructure.security.PasswordPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginAttemptService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginAttemptService.class);
    
    private final Map<String, LoginAttemptRecord> attempts = new ConcurrentHashMap<>();
    
    public void recordFailedAttempt(String username, String ipAddress) {
        String key = username.toLowerCase();
        LoginAttemptRecord record = attempts.computeIfAbsent(key, k -> new LoginAttemptRecord());
        
        record.failedAttempts++;
        record.lastAttemptTime = Instant.now().getEpochSecond();
        record.lastIpAddress = ipAddress;
        
        LOGGER.warn("Failed login attempt for user '{}' from IP '{}' (attempt {}/{})", 
            username, ipAddress, record.failedAttempts, PasswordPolicy.MAX_LOGIN_ATTEMPTS);
        
        if (record.failedAttempts >= PasswordPolicy.MAX_LOGIN_ATTEMPTS) {
            record.lockedUntil = Instant.now().getEpochSecond() + (PasswordPolicy.LOCKOUT_DURATION_MINUTES * 60);
            LOGGER.error("Account '{}' locked due to {} failed login attempts from IP '{}'", 
                username, record.failedAttempts, ipAddress);
        }
    }
    
    public void recordSuccessfulAttempt(String username) {
        String key = username.toLowerCase();
        attempts.remove(key);
        LOGGER.info("Successful login for user '{}'", username);
    }
    
    public boolean isAccountLocked(String username) {
        String key = username.toLowerCase();
        LoginAttemptRecord record = attempts.get(key);
        
        if (record == null) {
            return false;
        }
        
        if (record.lockedUntil == null) {
            return false;
        }
        
        long now = Instant.now().getEpochSecond();
        if (now >= record.lockedUntil) {
            attempts.remove(key);
            LOGGER.info("Account '{}' lockout expired, resetting attempts", username);
            return false;
        }
        
        return true;
    }
    
    public long getLockoutTimeRemaining(String username) {
        String key = username.toLowerCase();
        LoginAttemptRecord record = attempts.get(key);
        
        if (record == null || record.lockedUntil == null) {
            return 0;
        }
        
        long now = Instant.now().getEpochSecond();
        return Math.max(0, record.lockedUntil - now);
    }
    
    public int getFailedAttempts(String username) {
        String key = username.toLowerCase();
        LoginAttemptRecord record = attempts.get(key);
        return record == null ? 0 : record.failedAttempts;
    }
    
    public void unlockAccount(String username) {
        String key = username.toLowerCase();
        attempts.remove(key);
        LOGGER.info("Account '{}' manually unlocked by administrator", username);
    }
    
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry -> {
            LoginAttemptRecord record = entry.getValue();
            if (record.lockedUntil != null && now >= record.lockedUntil) {
                return true;
            }
            long timeSinceLastAttempt = now - record.lastAttemptTime;
            return timeSinceLastAttempt > (PasswordPolicy.LOCKOUT_DURATION_MINUTES * 60);
        });
    }
    
    private static class LoginAttemptRecord {
        int failedAttempts = 0;
        long lastAttemptTime = 0;
        Long lockedUntil = null;
        String lastIpAddress = null;
    }
}

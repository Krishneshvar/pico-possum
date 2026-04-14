package com.possum.infrastructure.security;

public final class SecurityConfig {
    
    private static volatile SecurityConfig instance;
    
    private final SessionConfig sessionConfig;
    private final RateLimitConfig rateLimitConfig;
    
    private SecurityConfig() {
        this.sessionConfig = new SessionConfig();
        this.rateLimitConfig = new RateLimitConfig();
    }
    
    public static SecurityConfig getInstance() {
        if (instance == null) {
            synchronized (SecurityConfig.class) {
                if (instance == null) {
                    instance = new SecurityConfig();
                }
            }
        }
        return instance;
    }
    
    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }
    
    public RateLimitConfig getRateLimitConfig() {
        return rateLimitConfig;
    }
    
    public static final class SessionConfig {
        private int timeoutMinutes = 30;
        private int maxSessionsPerUser = 5;
        private boolean enableFingerprinting = true;
        private int cleanupIntervalMinutes = 15;
        
        public int getTimeoutMinutes() {
            return timeoutMinutes;
        }
        
        public void setTimeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
        }
        
        public int getMaxSessionsPerUser() {
            return maxSessionsPerUser;
        }
        
        public void setMaxSessionsPerUser(int maxSessionsPerUser) {
            this.maxSessionsPerUser = maxSessionsPerUser;
        }
        
        public boolean isEnableFingerprinting() {
            return enableFingerprinting;
        }
        
        public void setEnableFingerprinting(boolean enableFingerprinting) {
            this.enableFingerprinting = enableFingerprinting;
        }
        
        public int getCleanupIntervalMinutes() {
            return cleanupIntervalMinutes;
        }
        
        public void setCleanupIntervalMinutes(int cleanupIntervalMinutes) {
            this.cleanupIntervalMinutes = cleanupIntervalMinutes;
        }
    }
    
    public static final class RateLimitConfig {
        private int maxLoginAttempts = 5;
        private int loginWindowSeconds = 300;
        private int lockoutDurationMinutes = 30;
        
        public int getMaxLoginAttempts() {
            return maxLoginAttempts;
        }
        
        public void setMaxLoginAttempts(int maxLoginAttempts) {
            this.maxLoginAttempts = maxLoginAttempts;
        }
        
        public int getLoginWindowSeconds() {
            return loginWindowSeconds;
        }
        
        public void setLoginWindowSeconds(int loginWindowSeconds) {
            this.loginWindowSeconds = loginWindowSeconds;
        }
        
        public int getLockoutDurationMinutes() {
            return lockoutDurationMinutes;
        }
        
        public void setLockoutDurationMinutes(int lockoutDurationMinutes) {
            this.lockoutDurationMinutes = lockoutDurationMinutes;
        }
    }
}

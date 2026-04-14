package com.possum.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecureTokenGenerator {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int DEFAULT_TOKEN_LENGTH = 32;
    
    public static String generateToken() {
        return generateToken(DEFAULT_TOKEN_LENGTH);
    }
    
    public static String generateToken(int byteLength) {
        byte[] tokenBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    public static String generateSessionFingerprint(String ipAddress, String userAgent) {
        String combined = ipAddress + "|" + userAgent;
        return String.valueOf(combined.hashCode());
    }
    
    private SecureTokenGenerator() {}
}

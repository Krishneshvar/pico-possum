package com.possum.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecureTokenGeneratorTest {

    @Test
    void generateToken_shouldReturnNonNullToken() {
        String token = SecureTokenGenerator.generateToken();
        
        assertNotNull(token);
    }

    @Test
    void generateToken_shouldReturnNonEmptyToken() {
        String token = SecureTokenGenerator.generateToken();
        
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_shouldReturnUrlSafeToken() {
        String token = SecureTokenGenerator.generateToken();
        
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void generateToken_shouldGenerateUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            tokens.add(SecureTokenGenerator.generateToken());
        }
        
        assertEquals(1000, tokens.size());
    }

    @Test
    void generateToken_withLength_shouldReturnTokenOfCorrectLength() {
        String token = SecureTokenGenerator.generateToken(16);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_withLength_shouldGenerateUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        
        for (int i = 0; i < 1000; i++) {
            tokens.add(SecureTokenGenerator.generateToken(16));
        }
        
        assertEquals(1000, tokens.size());
    }

    @Test
    void generateToken_withLength_shouldBeUrlSafe() {
        String token = SecureTokenGenerator.generateToken(64);
        
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void generateSessionFingerprint_shouldReturnNonNullFingerprint() {
        String fingerprint = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Mozilla/5.0");
        
        assertNotNull(fingerprint);
    }

    @Test
    void generateSessionFingerprint_shouldReturnConsistentFingerprint() {
        String fingerprint1 = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Mozilla/5.0");
        String fingerprint2 = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Mozilla/5.0");
        
        assertEquals(fingerprint1, fingerprint2);
    }

    @Test
    void generateSessionFingerprint_shouldReturnDifferentFingerprintForDifferentIp() {
        String fingerprint1 = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Mozilla/5.0");
        String fingerprint2 = SecureTokenGenerator.generateSessionFingerprint("192.168.1.1", "Mozilla/5.0");
        
        assertNotEquals(fingerprint1, fingerprint2);
    }

    @Test
    void generateSessionFingerprint_shouldReturnDifferentFingerprintForDifferentUserAgent() {
        String fingerprint1 = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Mozilla/5.0");
        String fingerprint2 = SecureTokenGenerator.generateSessionFingerprint("127.0.0.1", "Chrome/90.0");
        
        assertNotEquals(fingerprint1, fingerprint2);
    }

    @Test
    void generateToken_shouldHaveSufficientEntropy() {
        String token = SecureTokenGenerator.generateToken();
        
        assertTrue(token.length() >= 40);
    }

    @Test
    void generateToken_withSmallLength_shouldStillWork() {
        String token = SecureTokenGenerator.generateToken(1);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_withLargeLength_shouldStillWork() {
        String token = SecureTokenGenerator.generateToken(256);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
}

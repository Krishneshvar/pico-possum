package com.possum.infrastructure.logging;

import com.possum.domain.model.AuditLog;
import com.possum.domain.repositories.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLoggerTest {

    private AuditRepository auditRepository;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditRepository = mock(AuditRepository.class);
        auditLogger = new AuditLogger(auditRepository);
    }

    @Test
    void logAuthentication_shouldInsertAuditLog() {
        auditLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", "Mozilla", "User logged in");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logAuthentication_shouldHandleFailedLogin() {
        auditLogger.logAuthentication(1L, "LOGIN_FAILED", false, "127.0.0.1", "Mozilla", "Invalid password");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logAuthorization_shouldInsertAuditLogForGranted() {
        auditLogger.logAuthorization(1L, "sales.create", true, "127.0.0.1", "Access granted");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logAuthorization_shouldInsertAuditLogForDenied() {
        auditLogger.logAuthorization(1L, "admin.delete", false, "127.0.0.1", "Access denied");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logDataModification_shouldInsertAuditLog() {
        auditLogger.logDataModification(1L, "UPDATE", "products", 123L, "{\"name\":\"Old\"}", "{\"name\":\"New\"}");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logDataModification_withIpAddress_shouldInsertAuditLog() {
        auditLogger.logDataModification(1L, "UPDATE", "products", 123L, "{\"name\":\"Old\"}", "{\"name\":\"New\"}", "127.0.0.1");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logSecurityEvent_shouldInsertAuditLog() {
        auditLogger.logSecurityEvent(1L, "BRUTE_FORCE_DETECTED", "Multiple failed login attempts", "192.168.1.1", "warning");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logCriticalEvent_shouldInsertAuditLog() {
        auditLogger.logCriticalEvent(1L, "DATABASE_CORRUPTION", "Database integrity check failed", "127.0.0.1");
        
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logAuthentication_shouldNotThrowOnRepositoryFailure() {
        when(auditRepository.insertAuditLog(any())).thenThrow(new RuntimeException("DB error"));
        
        assertDoesNotThrow(() -> 
            auditLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", "Mozilla", "User logged in")
        );
    }

    @Test
    void logDataModification_shouldNotThrowOnRepositoryFailure() {
        when(auditRepository.insertAuditLog(any())).thenThrow(new RuntimeException("DB error"));
        
        assertDoesNotThrow(() -> 
            auditLogger.logDataModification(1L, "UPDATE", "products", 123L, "{}", "{}")
        );
    }

    @Test
    void verifyChainIntegrity_shouldReturnTrue() {
        boolean result = auditLogger.verifyChainIntegrity();
        assertTrue(result);
    }

    @Test
    void logAuthentication_shouldHandleNullUserAgent() {
        assertDoesNotThrow(() -> 
            auditLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", null, "User logged in")
        );
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logAuthorization_shouldHandleNullDetails() {
        assertDoesNotThrow(() -> 
            auditLogger.logAuthorization(1L, "sales.create", true, "127.0.0.1", null)
        );
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logDataModification_shouldHandleNullOldData() {
        assertDoesNotThrow(() -> 
            auditLogger.logDataModification(1L, "CREATE", "products", 123L, null, "{\"name\":\"New\"}")
        );
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }

    @Test
    void logDataModification_shouldHandleNullNewData() {
        assertDoesNotThrow(() -> 
            auditLogger.logDataModification(1L, "DELETE", "products", 123L, "{\"name\":\"Old\"}", null)
        );
        verify(auditRepository).insertAuditLog(any(AuditLog.class));
    }
}

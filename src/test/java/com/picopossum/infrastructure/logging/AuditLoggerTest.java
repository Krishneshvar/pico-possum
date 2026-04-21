package com.picopossum.infrastructure.logging;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.domain.repositories.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock private AuditRepository auditRepository;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLogger(auditRepository);
    }

    @Test
    @DisplayName("Should log authentication events properly for single-user core")
    void logAuthentication_success() {
        auditLogger.logAuthentication("LOGIN", true, "User logged in");

        verify(auditRepository).insertAuditLog(argThat(log -> 
            "LOGIN".equals(log.action()) && 
            "auth".equals(log.tableName()) &&
            "User logged in".equals(log.eventDetails())
        ));
    }

    @Test
    @DisplayName("Should log data modifications with correct mapping")
    void logDataModification_success() {
        auditLogger.logDataModification("UPDATE", "products", 123L, "{\"name\":\"Old\"}", "{\"name\":\"New\"}");

        verify(auditRepository).insertAuditLog(argThat(log -> 
            "UPDATE".equals(log.action()) && 
            "products".equals(log.tableName()) &&
            123L == log.rowId() &&
            "{\"name\":\"Old\"}".equals(log.oldData()) &&
            "{\"name\":\"New\"}".equals(log.newData())
        ));
    }

    @Test
    @DisplayName("Should log security events with severity mapping")
    void logSecurityEvent_success() {
        auditLogger.logSecurityEvent("BRUTE_FORCE_DETECTED", "Multiple failed login attempts", "warning");

        verify(auditRepository).insertAuditLog(argThat(log -> 
            "BRUTE_FORCE_DETECTED".equals(log.action()) && 
            "security".equals(log.tableName()) &&
            "Multiple failed login attempts".equals(log.eventDetails())
        ));
    }

    @Test
    @DisplayName("Should log critical events with highest severity standard")
    void logCriticalEvent_success() {
        auditLogger.logCriticalEvent("DATABASE_CORRUPTION", "Database integrity check failed");

        verify(auditRepository).insertAuditLog(argThat(log -> 
            "DATABASE_CORRUPTION".equals(log.action()) && 
            "Database integrity check failed".equals(log.eventDetails())
        ));
    }
}

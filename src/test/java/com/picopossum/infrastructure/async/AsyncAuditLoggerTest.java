package com.picopossum.infrastructure.async;

import com.picopossum.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncAuditLoggerTest {

    @Mock private AuditLogger mockAuditLogger;
    private AsyncAuditLogger asyncLogger;

    @BeforeEach
    void setUp() {
        asyncLogger = new AsyncAuditLogger(mockAuditLogger);
    }

    @AfterEach
    void tearDown() {
        asyncLogger.shutdown();
    }

    @Test
    @DisplayName("Should queue authentication events for background logging")
    void logAuthentication_enqueuesSuccessfully() {
        asyncLogger.logAuthentication("LOGIN", true, "Success");

        verify(mockAuditLogger, timeout(2000)).logAuthentication(
                eq("LOGIN"), eq(true), eq("Success")
        );
    }

    @Test
    @DisplayName("Should queue data modification events")
    void logDataModification_enqueuesSuccessfully() {
        asyncLogger.logDataModification("CREATE", "sales", 123L, null, "new data");

        verify(mockAuditLogger, timeout(2000)).logDataModification(
                eq("CREATE"), eq("sales"), eq(123L), isNull(), eq("new data")
        );
    }

    @Test
    @DisplayName("Should queue security events")
    void logSecurityEvent_enqueuesSuccessfully() {
        asyncLogger.logSecurityEvent("PASSWORD_CHANGE", "Changed password", "info");

        verify(mockAuditLogger, timeout(2000)).logSecurityEvent(
                eq("PASSWORD_CHANGE"), eq("Changed password"), eq("info")
        );
    }

    @Test
    @DisplayName("Should log critical events synchronously for immediate persistence")
    void logCriticalEvent_executedSynchronously() {
        asyncLogger.logCriticalEvent("SECURITY_BREACH", "Critical event");

        verify(mockAuditLogger, times(1)).logCriticalEvent(
                eq("SECURITY_BREACH"), eq("Critical event")
        );
    }

    @Test
    @DisplayName("Should maintain accurate stats for the audit queue")
    void getStats_returnsAccurateCounts() throws InterruptedException {
        // Slow down processing to ensure queue size is reflected
        asyncLogger.logDataModification("CREATE", "sales", 1L, null, "data");
        asyncLogger.logDataModification("UPDATE", "sales", 2L, "old", "new");

        AsyncAuditLogger.AsyncAuditStats stats = asyncLogger.getStats();
        assertTrue(stats.queued() >= 2);
    }
}

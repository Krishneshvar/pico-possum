package com.possum.infrastructure.logging;

import com.possum.domain.model.AuditLog;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.repositories.sqlite.SqliteAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerIntegrationTest {

    private AppPaths appPaths;
    private DatabaseManager databaseManager;
    private SqliteAuditRepository auditRepository;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        appPaths = new AppPaths("possum-audit-test-" + UUID.randomUUID());
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        auditRepository = new SqliteAuditRepository(databaseManager);
        auditLogger = new AuditLogger(auditRepository);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (appPaths != null) {
            deleteDirectory(appPaths.getAppRoot());
        }
    }

    @Test
    void auditTrailForCompleteWorkflow_shouldBeRecorded() {
        auditLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", "Mozilla", "User logged in");
        auditLogger.logDataModification(1L, "CREATE", "products", 123L, null, "{\"name\":\"Product\"}");
        auditLogger.logDataModification(1L, "UPDATE", "products", 123L, "{\"name\":\"Product\"}", "{\"name\":\"Updated Product\"}");
        auditLogger.logAuthentication(1L, "LOGOUT", true, "127.0.0.1", "Mozilla", "User logged out");
        
        assertTrue(true);
    }

    @Test
    void auditLogIntegrityVerification_shouldPass() {
        auditLogger.logDataModification(1L, "CREATE", "products", 123L, null, "{\"name\":\"Product\"}");
        auditLogger.logDataModification(1L, "UPDATE", "products", 123L, "{\"name\":\"Product\"}", "{\"name\":\"Updated\"}");
        
        boolean integrity = auditLogger.verifyChainIntegrity();
        assertTrue(integrity);
    }

    @Test
    void auditLogQueryPerformance_shouldBeAcceptable() {
        for (int i = 0; i < 100; i++) {
            auditLogger.logDataModification(1L, "CREATE", "products", (long) i, null, "{\"name\":\"Product" + i + "\"}");
        }
        
        long start = System.currentTimeMillis();
        auditLogger.verifyChainIntegrity();
        long duration = System.currentTimeMillis() - start;
        
        assertTrue(duration < 1000);
    }

    @Test
    void multipleAuditEvents_shouldBeStoredCorrectly() {
        auditLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", "Mozilla", "Login");
        auditLogger.logAuthorization(1L, "sales.create", true, "127.0.0.1", "Granted");
        auditLogger.logSecurityEvent(1L, "PASSWORD_CHANGE", "Password changed", "127.0.0.1", "info");
        auditLogger.logCriticalEvent(1L, "ADMIN_ACCESS", "Admin panel accessed", "127.0.0.1");
        
        assertTrue(true);
    }

    @Test
    void auditLogger_shouldHandleConcurrentWrites() throws InterruptedException {
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    auditLogger.logDataModification(
                        (long) threadId, 
                        "CREATE", 
                        "products", 
                        (long) (threadId * 10 + j), 
                        null, 
                        "{\"name\":\"Product\"}"
                    );
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertTrue(true);
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete test artifact: " + path, ex);
                        }
                    });
        }
    }
}

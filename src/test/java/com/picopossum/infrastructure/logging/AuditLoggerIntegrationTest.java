package com.picopossum.infrastructure.logging;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.repositories.sqlite.SqliteAuditRepository;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static SqliteAuditRepository auditRepository;
    private static AuditLogger auditLogger;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-audit-int-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        
        auditRepository = new SqliteAuditRepository(databaseManager);
        auditLogger = new AuditLogger(auditRepository);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @DisplayName("Should persist multiple audit events in the physical database")
    void logEvents_persistsToDatabase() {
        auditLogger.logAuthentication("LOGIN", true, "User logged in");
        auditLogger.logDataModification("CREATE", "products", 123L, null, "{\"name\":\"Product\"}");
        auditLogger.logDataModification("UPDATE", "products", 123L, "{\"name\":\"Product\"}", "{\"name\":\"Updated Product\"}");
        auditLogger.logAuthentication("LOGOUT", true, "User logged out");

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "created_at", "DESC", 1, 10);
        PagedResult<AuditLog> result = auditRepository.findAuditLogs(filter);

        assertTrue(result.totalCount() >= 4);
    }

    @Test
    @DisplayName("Should maintain chain integrity during rapid multi-event logging")
    void logEvents_maintainsIntegrity() {
        auditLogger.logDataModification("CREATE", "products", 123L, null, "{\"name\":\"Product\"}");
        auditLogger.logDataModification("UPDATE", "products", 123L, "{\"name\":\"Product\"}", "{\"name\":\"Updated\"}");

        assertTrue(auditLogger.verifyChainIntegrity());
    }

    @Test
    @DisplayName("Should handle logging within the retention limit automatically")
    void logEvents_withinRetentionLimit() {
        for (int i = 0; i < 50; i++) {
            auditLogger.logDataModification("CREATE", "products", (long) i, null, "{\"name\":\"Product" + i + "\"}");
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "created_at", "DESC", 1, 100);
        PagedResult<AuditLog> result = auditRepository.findAuditLogs(filter);
        assertTrue(result.totalCount() >= 50);
    }

    @Test
    @DisplayName("Should support varied log types without user identity overhead")
    void logVariedEvents_success() {
        auditLogger.logAuthentication("LOGIN", true, "Login");
        auditLogger.logSecurityEvent("PASSWORD_CHANGE", "Password changed", "info");
        auditLogger.logCriticalEvent("ADMIN_ACCESS", "Admin panel accessed");

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "created_at", "DESC", 1, 10);
        PagedResult<AuditLog> result = auditRepository.findAuditLogs(filter);
        assertTrue(result.totalCount() >= 3);
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete: " + path, ex);
                }
            });
        }
    }
}

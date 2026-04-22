package com.picopossum.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.picopossum.domain.model.AuditLog;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.util.TimeUtil;

import java.util.Objects;

/**
 * Modernized Audit Service for Single-User SMB.
 * Removed identity tracking and simplified the record structure.
 */
public final class AuditService {

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.atomic.AtomicLong logCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private volatile String lastHash = "GENESIS";
    
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-logger");
        t.setDaemon(true);
        return t;
    });

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        initializeLastHash();
    }

    private void initializeLastHash() {
        try {
            AuditLogFilter filter = new AuditLogFilter(
                    null, null, null, null, null, null,
                    "created_at", "DESC", 1, 1
            );
            PagedResult<AuditLog> latest = auditRepository.findAuditLogs(filter);
            if (latest != null && !latest.items().isEmpty()) {
                AuditLog log = latest.items().get(0);
                if (log.integrityHash() != null) {
                    this.lastHash = log.integrityHash();
                }
            }
        } catch (Exception e) {
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to initialize audit last hash", e);
        }
    }

    /**
     * Record a generic audit event asynchronously
     */
    public void recordEvent(String action, String tableName, Long rowId,
                             Object oldData, Object newData, Object eventDetails, String severity) {
        Objects.requireNonNull(action, "action must not be null");
        final String effectiveSeverity = severity != null ? severity : "info";

        executor.submit(() -> {
            try {
                String oldDataJson = toJson(oldData);
                String newDataJson = toJson(newData);
                String detailsJson = toJson(eventDetails);
                java.time.LocalDateTime now = TimeUtil.nowUTC();

                // 1. Create temporary log to calculate hash
                AuditLog tempLog = new AuditLog(
                        null, action, tableName, rowId, oldDataJson, newDataJson, 
                        detailsJson, effectiveSeverity, now, null
                );
                
                // 2. Calculate integrity hash
                lastHash = calculateIntegrityHash(tempLog, lastHash);
                
                // 3. Create final log with hash
                AuditLog auditLog = new AuditLog(
                        null, action, tableName, rowId, oldDataJson, newDataJson, 
                        detailsJson, effectiveSeverity, now, lastHash
                );
                
                auditRepository.insertAuditLog(auditLog);
                
                // Periodic cleanup
                if (logCounter.incrementAndGet() % 20 == 0) {
                    auditRepository.cleanupOldLogs();
                }
            } catch (Exception e) {
                com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to record audit event: {}", action, e);
            }
        });
    }

    private String calculateIntegrityHash(AuditLog log, String previousHash) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String data = previousHash + "|" + log.createdAt() + "|" + log.action() + "|" + 
                         log.tableName() + "|" + log.eventDetails();
            byte[] hashBytes = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            return "HASH_ERROR";
        }
    }

    public void recordEvent(String action, String tableName, Long rowId,
                            Object oldData, Object newData, Object eventDetails) {
        recordEvent(action, tableName, rowId, oldData, newData, eventDetails, "info");
    }

    public void logLogin(Object eventDetails) {
        recordEvent("login", null, null, null, null, eventDetails, "info");
    }

    public void logLogout(Object eventDetails) {
        recordEvent("logout", null, null, null, null, eventDetails, "info");
    }

    public void logCreate(String tableName, Long rowId, Object newData) {
        recordEvent("create", tableName, rowId, null, newData, null, "info");
    }

    public void logUpdate(String tableName, Long rowId, Object oldData, Object newData) {
        recordEvent("update", tableName, rowId, oldData, newData, null, "info");
    }

    public void logUpdate(String tableName, Long rowId, Object oldData, Object newData, Object eventDetails) {
        recordEvent("update", tableName, rowId, oldData, newData, eventDetails, "info");
    }

    public void logDelete(String tableName, Long rowId, Object oldData) {
        recordEvent("delete", tableName, rowId, oldData, null, null, "warning");
    }

    public void logSecurityEvent(String action, Object details, String severity) {
        recordEvent(action, "security", null, null, null, details, severity);
    }

    public AuditLog getAuditEvent(Long auditLogId) {
        Objects.requireNonNull(auditLogId, "auditLogId must not be null");
        return auditRepository.findAuditLogById(auditLogId);
    }

    public PagedResult<AuditLog> listAuditEvents(AuditLogFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return auditRepository.findAuditLogs(filter);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize data\"}";
        }
    }
}

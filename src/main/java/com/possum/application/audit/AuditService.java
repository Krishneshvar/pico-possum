package com.possum.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possum.domain.model.AuditLog;
import com.possum.domain.repositories.AuditRepository;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;

import java.util.List;
import java.util.Objects;

public final class AuditService {

    private final AuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Record a generic audit event
     */
    public long recordEvent(Long userId, String action, String tableName, Long rowId,
                            Object oldData, Object newData, Object eventDetails) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(action, "action must not be null");

        try {
            AuditLog auditLog = new AuditLog(
                    null,
                    userId,
                    action,
                    tableName,
                    rowId,
                    toJson(oldData),
                    toJson(newData),
                    toJson(eventDetails),
                    null,
                    null
            );
            return auditRepository.insertAuditLog(auditLog);
        } catch (Exception e) {
            // Audit failures should not break business operations
            System.err.println("Failed to record audit event: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Record a login event
     */
    public long logLogin(Long userId, Object eventDetails) {
        return recordEvent(userId, "login", null, null, null, null, eventDetails);
    }

    /**
     * Record a logout event
     */
    public long logLogout(Long userId, Object eventDetails) {
        return recordEvent(userId, "logout", null, null, null, null, eventDetails);
    }

    /**
     * Record a create operation
     */
    public long logCreate(Long userId, String tableName, Long rowId, Object newData) {
        return recordEvent(userId, "create", tableName, rowId, null, newData, null);
    }

    /**
     * Record an update operation
     */
    public long logUpdate(Long userId, String tableName, Long rowId, Object oldData, Object newData) {
        return recordEvent(userId, "update", tableName, rowId, oldData, newData, null);
    }

    /**
     * Record an update operation with event details
     */
    public long logUpdate(Long userId, String tableName, Long rowId, Object oldData, Object newData, Object eventDetails) {
        return recordEvent(userId, "update", tableName, rowId, oldData, newData, eventDetails);
    }

    /**
     * Record a delete operation
     */
    public long logDelete(Long userId, String tableName, Long rowId, Object oldData) {
        return recordEvent(userId, "delete", tableName, rowId, oldData, null, null);
    }

    /**
     * Get a single audit event by ID
     */
    public AuditLog getAuditEvent(Long auditLogId) {
        Objects.requireNonNull(auditLogId, "auditLogId must not be null");
        return auditRepository.findAuditLogById(auditLogId);
    }

    /**
     * List audit events with filtering and pagination
     */
    public PagedResult<AuditLog> listAuditEvents(AuditLogFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return auditRepository.findAuditLogs(filter);
    }

    /**
     * List audit events by user
     */
    public List<AuditLog> listAuditEventsByUser(Long userId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        
        AuditLogFilter filter = new AuditLogFilter(
                null, null, userId, null, null, null, null,
                "created_at", "DESC", 1, limit
        );
        return auditRepository.findAuditLogs(filter).items();
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

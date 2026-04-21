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

    public AuditService(AuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Record a generic audit event
     */
    public long recordEvent(String action, String tableName, Long rowId,
                            Object oldData, Object newData, Object eventDetails) {
        Objects.requireNonNull(action, "action must not be null");

        try {
            AuditLog auditLog = new AuditLog(
                    null,
                    action,
                    tableName,
                    rowId,
                    toJson(oldData),
                    toJson(newData),
                    toJson(eventDetails),
                    TimeUtil.nowUTC()
            );
            return auditRepository.insertAuditLog(auditLog);
        } catch (Exception e) {
            System.err.println("Failed to record audit event: " + e.getMessage());
            return -1;
        }
    }

    public long logLogin(Object eventDetails) {
        return recordEvent("login", null, null, null, null, eventDetails);
    }

    public long logLogout(Object eventDetails) {
        return recordEvent("logout", null, null, null, null, eventDetails);
    }

    public long logCreate(String tableName, Long rowId, Object newData) {
        return recordEvent("create", tableName, rowId, null, newData, null);
    }

    public long logUpdate(String tableName, Long rowId, Object oldData, Object newData) {
        return recordEvent("update", tableName, rowId, oldData, newData, null);
    }

    public long logUpdate(String tableName, Long rowId, Object oldData, Object newData, Object eventDetails) {
        return recordEvent("update", tableName, rowId, oldData, newData, eventDetails);
    }

    public long logDelete(String tableName, Long rowId, Object oldData) {
        return recordEvent("delete", tableName, rowId, oldData, null, null);
    }

    public AuditLog getAuditEvent(Long auditLogId) {
        Objects.requireNonNull(auditLogId, "auditLogId must not be null");
        return auditRepository.findAuditLogById(auditLogId);
    }

    public PagedResult<AuditLog> listAuditEvents(AuditLogFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return auditRepository.findAuditLogs(filter);
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

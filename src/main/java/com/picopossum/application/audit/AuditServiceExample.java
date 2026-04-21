package com.picopossum.application.audit;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Example usage of AuditService (Modernized for Single-User SMB)
 * Demonstrates how to record and query audit events without redundant identity tracking.
 */
public class AuditServiceExample {

    private final AuditService auditService;

    public AuditServiceExample(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Example: Record authentication events
     */
    public void recordAuthenticationEvents() {
        // Login event
        Map<String, String> loginDetails = new HashMap<>();
        loginDetails.put("status", "Successful");
        auditService.logLogin(loginDetails);

        // Logout event
        auditService.logLogout(null);
    }

    /**
     * Example: Record product creation
     */
    public void recordProductCreation(Long productId) {
        Map<String, Object> productData = new HashMap<>();
        productData.put("name", "New Product");
        productData.put("status", "active");
        productData.put("category_id", 5);

        auditService.logCreate("products", productId, productData);
    }

    /**
     * Example: Record product update
     */
    public void recordProductUpdate(Long productId) {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("name", "Old Name");

        Map<String, Object> newData = new HashMap<>();
        newData.put("name", "New Name");

        auditService.logUpdate("products", productId, oldData, newData);
    }

    /**
     * Example: Record sale cancellation with reason
     */
    public void recordSaleCancellation(Long saleId) {
        Map<String, String> oldData = new HashMap<>();
        oldData.put("status", "paid");

        Map<String, String> newData = new HashMap<>();
        newData.put("status", "cancelled");

        Map<String, String> eventDetails = new HashMap<>();
        eventDetails.put("reason", "Customer request");

        auditService.logUpdate("sales", saleId, oldData, newData, eventDetails);
    }

    /**
     * Example: Query all audit events with pagination
     */
    public PagedResult<AuditLog> queryAllAuditLogs(int page, int pageSize) {
        AuditLogFilter filter = new AuditLogFilter(
                null,           // tableName
                null,           // rowId
                null,           // actions
                null,           // startDate
                null,           // endDate
                null,           // searchTerm
                "created_at",   // sortBy
                "DESC",         // sortOrder
                page,           // currentPage
                pageSize        // itemsPerPage
        );

        return auditService.listAuditEvents(filter);
    }

    /**
     * Example: Query audit events for a specific record
     */
    public PagedResult<AuditLog> queryRecordHistory(String tableName, Long recordId) {
        AuditLogFilter filter = new AuditLogFilter(
                tableName,      // tableName
                recordId,       // rowId
                null,           // actions
                null,           // startDate
                null,           // endDate
                null,           // searchTerm
                "created_at",   // sortBy
                "ASC",          // sortOrder - chronological
                1,              // currentPage
                100             // itemsPerPage
        );

        return auditService.listAuditEvents(filter);
    }

    /**
     * Example: Get a specific audit event
     */
    public AuditLog getAuditEventById(Long auditLogId) {
        return auditService.getAuditEvent(auditLogId);
    }
}

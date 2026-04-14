package com.possum.application.audit;

import com.possum.domain.model.AuditLog;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example usage of AuditService
 * Demonstrates how to record and query audit events
 */
public class AuditServiceExample {

    private final AuditService auditService;

    public AuditServiceExample(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Example: Record authentication events
     */
    public void recordAuthenticationEvents(Long userId) {
        // Login event with details
        Map<String, String> loginDetails = new HashMap<>();
        loginDetails.put("ip", "192.168.1.100");
        loginDetails.put("userAgent", "Mozilla/5.0");
        auditService.logLogin(userId, loginDetails);

        // Logout event
        auditService.logLogout(userId, null);
    }

    /**
     * Example: Record product creation
     */
    public void recordProductCreation(Long userId, Long productId) {
        Map<String, Object> productData = new HashMap<>();
        productData.put("name", "New Product");
        productData.put("status", "active");
        productData.put("category_id", 5);

        auditService.logCreate(userId, "products", productId, productData);
    }

    /**
     * Example: Record product update
     */
    public void recordProductUpdate(Long userId, Long productId) {
        Map<String, Object> oldData = new HashMap<>();
        oldData.put("name", "Old Name");
        oldData.put("status", "inactive");

        Map<String, Object> newData = new HashMap<>();
        newData.put("name", "New Name");
        newData.put("status", "active");

        auditService.logUpdate(userId, "products", productId, oldData, newData);
    }

    /**
     * Example: Record sale creation with event details
     */
    public void recordSaleCreation(Long userId, Long saleId) {
        Map<String, Object> saleData = new HashMap<>();
        saleData.put("invoice_number", "INV-2024-001");
        saleData.put("total_amount", 150.00);
        saleData.put("items_count", 3);

        auditService.logCreate(userId, "sales", saleId, saleData);
    }

    /**
     * Example: Record sale cancellation with reason
     */
    public void recordSaleCancellation(Long userId, Long saleId) {
        Map<String, String> oldData = new HashMap<>();
        oldData.put("status", "paid");

        Map<String, String> newData = new HashMap<>();
        newData.put("status", "cancelled");

        Map<String, String> eventDetails = new HashMap<>();
        eventDetails.put("reason", "Customer request");

        auditService.logUpdate(userId, "sales", saleId, oldData, newData, eventDetails);
    }

    /**
     * Example: Query all audit events with pagination
     */
    public PagedResult<AuditLog> queryAllAuditLogs(int page, int pageSize) {
        AuditLogFilter filter = new AuditLogFilter(
                null,           // tableName
                null,           // rowId
                null,           // userId
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
     * Example: Query audit events for a specific table
     */
    public PagedResult<AuditLog> queryProductAuditLogs() {
        AuditLogFilter filter = new AuditLogFilter(
                "products",     // tableName
                null,           // rowId
                null,           // userId
                null,           // actions
                null,           // startDate
                null,           // endDate
                null,           // searchTerm
                "created_at",   // sortBy
                "DESC",         // sortOrder
                1,              // currentPage
                50              // itemsPerPage
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
                null,           // userId
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
     * Example: Query user activity
     */
    public List<AuditLog> queryUserActivity(Long userId, int limit) {
        return auditService.listAuditEventsByUser(userId, limit);
    }

    /**
     * Example: Search audit logs
     */
    public PagedResult<AuditLog> searchAuditLogs(String searchTerm) {
        AuditLogFilter filter = new AuditLogFilter(
                null,           // tableName
                null,           // rowId
                null,           // userId
                null,           // actions
                null,           // startDate
                null,           // endDate
                searchTerm,     // searchTerm
                "created_at",   // sortBy
                "DESC",         // sortOrder
                1,              // currentPage
                50              // itemsPerPage
        );

        return auditService.listAuditEvents(filter);
    }

    /**
     * Example: Query audit events by date range
     */
    public PagedResult<AuditLog> queryAuditLogsByDateRange(String startDate, String endDate) {
        AuditLogFilter filter = new AuditLogFilter(
                null,           // tableName
                null,           // rowId
                null,           // userId
                null,           // actions
                startDate,      // startDate (e.g., "2024-01-01")
                endDate,        // endDate (e.g., "2024-12-31")
                null,           // searchTerm
                "created_at",   // sortBy
                "DESC",         // sortOrder
                1,              // currentPage
                50              // itemsPerPage
        );

        return auditService.listAuditEvents(filter);
    }

    /**
     * Example: Query specific action types
     */
    public PagedResult<AuditLog> queryLoginEvents() {
        AuditLogFilter filter = new AuditLogFilter(
                null,           // tableName
                null,           // rowId
                null,           // userId
                java.util.List.of("login"), // actions
                null,           // startDate
                null,           // endDate
                null,           // searchTerm
                "created_at",   // sortBy
                "DESC",         // sortOrder
                1,              // currentPage
                50              // itemsPerPage
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

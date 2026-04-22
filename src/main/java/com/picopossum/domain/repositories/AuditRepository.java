package com.picopossum.domain.repositories;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.util.TimeUtil;

/**
 * Minimalist repository for system auditing.
 * Removed userId from the logging contract for a Single-User environment.
 */
public interface AuditRepository {
    long insertAuditLog(AuditLog auditLog);

    PagedResult<AuditLog> findAuditLogs(AuditLogFilter filter);
    
    AuditLog findAuditLogById(Long id);

    void cleanupOldLogs();
    
    /**
     * Simplified logging helper for single-user context.
     */
    default void log(String tableName, long rowId, String action, String data) {
        insertAuditLog(new AuditLog(
            null, action, tableName, rowId, null, data, null, "info", TimeUtil.nowUTC(), null
        ));
    }
}

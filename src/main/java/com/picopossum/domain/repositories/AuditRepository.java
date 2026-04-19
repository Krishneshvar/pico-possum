package com.picopossum.domain.repositories;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;

public interface AuditRepository {
    long insertAuditLog(AuditLog auditLog);

    PagedResult<AuditLog> findAuditLogs(AuditLogFilter filter);
    
    AuditLog findAuditLogById(Long id);
    
    default void log(String tableName, long rowId, String action, String data, long userId) {
        insertAuditLog(new com.picopossum.domain.model.AuditLog(
            null, userId, action, tableName, rowId, null, data, null, null, com.picopossum.shared.util.TimeUtil.nowUTC()
        ));
    }
}


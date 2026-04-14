package com.possum.domain.repositories;

import com.possum.domain.model.AuditLog;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;

public interface AuditRepository {
    long insertAuditLog(AuditLog auditLog);

    PagedResult<AuditLog> findAuditLogs(AuditLogFilter filter);
    
    AuditLog findAuditLogById(Long id);
    
    default void log(String tableName, long rowId, String action, String data, long userId) {
        insertAuditLog(new com.possum.domain.model.AuditLog(
            null, userId, action, tableName, rowId, null, data, null, null, com.possum.shared.util.TimeUtil.nowUTC()
        ));
    }
}


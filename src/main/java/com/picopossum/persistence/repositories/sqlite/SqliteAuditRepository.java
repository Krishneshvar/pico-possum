package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.AuditLogMapper;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Minimalist implementation of AuditRepository.
 * Simplified for Single-User SMB (Removed User joins).
 */
public final class SqliteAuditRepository extends BaseSqliteRepository implements AuditRepository {

    private static final int MAX_AUDIT_LOGS = 1000;

    private final AuditLogMapper auditLogMapper = new AuditLogMapper();

    public SqliteAuditRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    public SqliteAuditRepository(ConnectionProvider connectionProvider, com.picopossum.infrastructure.monitoring.PerformanceMonitor performanceMonitor) {
        super(connectionProvider, performanceMonitor);
    }

    @Override
    public long insertAuditLog(AuditLog auditLog) {
        return executeInsert(
                """
                INSERT INTO audit_log (action, table_name, row_id, old_data, new_data, event_details, severity, integrity_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                auditLog.action(),
                auditLog.tableName(),
                auditLog.rowId(),
                auditLog.oldData(),
                auditLog.newData(),
                auditLog.eventDetails(),
                auditLog.severity(),
                auditLog.integrityHash()
        );
    }

    @Override
    public void cleanupOldLogs() {
        executeUpdate(
                """
                DELETE FROM audit_log 
                WHERE id IN (
                    SELECT id FROM audit_log 
                    ORDER BY id DESC 
                    LIMIT -1 OFFSET ?
                )
                """,
                MAX_AUDIT_LOGS
        );
    }

    @Override
    public AuditLog findAuditLogById(Long id) {
        return queryOne(
                "SELECT * FROM audit_log WHERE id = ?",
                auditLogMapper,
                id
        ).orElse(null);
    }

    @Override
    public PagedResult<AuditLog> findAuditLogs(AuditLogFilter filter) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildWhere(filter, params);

        int totalCount = queryOne(
                "SELECT COUNT(*) AS count FROM audit_log " + whereClause,
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortBy = filter.sortBy() == null ? "created_at" : filter.sortBy();
        String sortOrder = "ASC".equalsIgnoreCase(filter.sortOrder()) ? "ASC" : "DESC";

        int page = Math.max(1, filter.currentPage() + 1);
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        params.add(limit);
        params.add(offset);

        List<AuditLog> logs = queryList(
                """
                SELECT * FROM audit_log
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(whereClause, sortBy, sortOrder),
                auditLogMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) totalCount / limit);
        return new PagedResult<>(logs, totalCount, totalPages, page, limit);
    }

    private static String buildWhere(AuditLogFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        if (filter.tableName() != null && !filter.tableName().isBlank()) {
            joiner.add("table_name = ?");
            params.add(filter.tableName());
        }
        if (filter.rowId() != null) {
            joiner.add("row_id = ?");
            params.add(filter.rowId());
        }
        if (filter.actions() != null && !filter.actions().isEmpty()) {
            StringJoiner inJoiner = new StringJoiner(",", "action IN (", ")");
            for (String action : filter.actions()) {
                inJoiner.add("?");
                params.add(action);
            }
            joiner.add(inJoiner.toString());
        }
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            joiner.add("created_at >= ?");
            params.add(filter.startDate() + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            joiner.add("created_at <= ?");
            params.add(filter.endDate() + " 23:59:59");
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            joiner.add("(action LIKE ? OR table_name LIKE ? OR event_details LIKE ?)");
            String fuzzy = "%" + filter.searchTerm() + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (joiner.length() == 0) {
            return "";
        }
        return "WHERE " + joiner;
    }
}

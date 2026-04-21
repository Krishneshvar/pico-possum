package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimalist mapper for single-user audit logs.
 */
public final class AuditLogMapper implements RowMapper<AuditLog> {
    @Override
    public AuditLog map(ResultSet rs) throws SQLException {
        return new AuditLog(
                rs.getLong("id"),
                rs.getString("action"),
                rs.getString("table_name"),
                SqlMapperUtils.getNullableLong(rs, "row_id"),
                rs.getString("old_data"),
                rs.getString("new_data"),
                rs.getString("event_details"),
                rs.getString("severity"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at")
        );
    }
}

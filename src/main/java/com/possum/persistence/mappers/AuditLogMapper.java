package com.possum.persistence.mappers;

import com.possum.domain.model.AuditLog;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class AuditLogMapper implements RowMapper<AuditLog> {
    @Override
    public AuditLog map(ResultSet rs) throws SQLException {
        return new AuditLog(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("action"),
                rs.getString("table_name"),
                getNullableLong(rs, "row_id"),
                rs.getString("old_data"),
                rs.getString("new_data"),
                rs.getString("event_details"),
                getOptionalColumn(rs, "user_name"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

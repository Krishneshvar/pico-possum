package com.possum.persistence.mappers;

import com.possum.domain.model.Supplier;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class SupplierMapper implements RowMapper<Supplier> {
    @Override
    public Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("contact_person"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getString("gstin"),
                getNullableLong(rs, "payment_policy_id"),
                getOptionalColumn(rs, "payment_policy_name"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
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

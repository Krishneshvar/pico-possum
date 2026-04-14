package com.possum.persistence.mappers;

import com.possum.domain.model.InventoryAdjustment;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class InventoryAdjustmentMapper implements RowMapper<InventoryAdjustment> {
    @Override
    public InventoryAdjustment map(ResultSet rs) throws SQLException {
        return new InventoryAdjustment(
                rs.getLong("id"),
                rs.getLong("variant_id"),
                getNullableLong(rs, "lot_id"),
                rs.getInt("quantity_change"),
                rs.getString("reason"),
                rs.getString("reference_type"),
                getNullableLong(rs, "reference_id"),
                getNullableLong(rs, "adjusted_by"),
                getOptionalColumn(rs, "adjusted_by_name"),
                SqlMapperUtils.getLocalDateTime(rs, "adjusted_at")
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

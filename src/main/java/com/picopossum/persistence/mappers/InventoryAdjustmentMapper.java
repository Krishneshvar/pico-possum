package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.InventoryAdjustment;
import com.picopossum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class InventoryAdjustmentMapper implements RowMapper<InventoryAdjustment> {
    @Override
    public InventoryAdjustment map(ResultSet rs) throws SQLException {
        return new InventoryAdjustment(
                rs.getLong("id"),
                rs.getLong("product_id"),
                SqlMapperUtils.getNullableLong(rs, "lot_id"),
                rs.getInt("quantity_change"),
                rs.getString("reason"),
                rs.getString("reference_type"),
                SqlMapperUtils.getNullableLong(rs, "reference_id"),
                SqlMapperUtils.getNullableLong(rs, "adjusted_by"),
                getOptionalColumn(rs, "adjusted_by_name"),
                SqlMapperUtils.getLocalDateTime(rs, "adjusted_at")
        );
    }

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

package com.possum.persistence.mappers;

import com.possum.domain.model.InventoryLot;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class InventoryLotMapper implements RowMapper<InventoryLot> {
    @Override
    public InventoryLot map(ResultSet rs) throws SQLException {
        return new InventoryLot(
                rs.getLong("id"),
                rs.getLong("variant_id"),
                rs.getString("batch_number"),
                SqlMapperUtils.getLocalDateTime(rs, "manufactured_date"),
                SqlMapperUtils.getLocalDateTime(rs, "expiry_date"),
                rs.getInt("quantity"),
                SqlMapperUtils.getBigDecimal(rs, "unit_cost"),
                getNullableLong(rs, "purchase_order_item_id"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}

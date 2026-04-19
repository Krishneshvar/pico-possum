package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.InventoryLot;
import com.picopossum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class InventoryLotMapper implements RowMapper<InventoryLot> {
    @Override
    public InventoryLot map(ResultSet rs) throws SQLException {
        return new InventoryLot(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("batch_number"),
                SqlMapperUtils.getLocalDateTime(rs, "manufactured_date"),
                SqlMapperUtils.getLocalDateTime(rs, "expiry_date"),
                rs.getInt("quantity"),
                SqlMapperUtils.getBigDecimal(rs, "unit_cost"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at")
        );
    }
}

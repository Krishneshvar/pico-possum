package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.StockMovement;
import com.picopossum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimalist mapper for single-user stock movements.
 */
public final class StockMovementMapper implements RowMapper<StockMovement> {
    @Override
    public StockMovement map(ResultSet rs) throws SQLException {
        return new StockMovement(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getInt("quantity_change"),
                rs.getString("reason"),
                rs.getString("reference_type"),
                SqlMapperUtils.getNullableLong(rs, "reference_id"),
                rs.getString("notes"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at")
        );
    }
}

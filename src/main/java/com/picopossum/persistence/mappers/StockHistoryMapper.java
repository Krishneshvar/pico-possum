package com.picopossum.persistence.mappers;

import com.picopossum.shared.dto.StockHistoryDto;
import com.picopossum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimalist mapper for stock history lookups.
 */
public final class StockHistoryMapper implements RowMapper<StockHistoryDto> {
    @Override
    public StockHistoryDto map(ResultSet rs) throws SQLException {
        return new StockHistoryDto(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getInt("quantity_change"),
                rs.getString("reason"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                rs.getInt("current_stock"),
                rs.getInt("stock_alert_cap")
        );
    }
}

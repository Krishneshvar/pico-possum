package com.possum.persistence.mappers;

import com.possum.shared.dto.StockHistoryDto;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class StockHistoryMapper implements RowMapper<StockHistoryDto> {
    @Override
    public StockHistoryDto map(ResultSet rs) throws SQLException {
        return new StockHistoryDto(
                rs.getLong("id"),
                rs.getLong("variant_id"),
                rs.getString("product_name"),
                rs.getString("variant_name"),
                rs.getString("sku"),
                rs.getInt("quantity_change"),
                rs.getString("reason"),
                rs.getString("adjusted_by_name"),
                SqlMapperUtils.getLocalDateTime(rs, "adjusted_at")
        );
    }
}

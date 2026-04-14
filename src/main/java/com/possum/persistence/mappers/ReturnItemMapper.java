package com.possum.persistence.mappers;

import com.possum.domain.model.ReturnItem;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class ReturnItemMapper implements RowMapper<ReturnItem> {
    @Override
    public ReturnItem map(ResultSet rs) throws SQLException {
        return new ReturnItem(
                rs.getLong("id"),
                rs.getLong("return_id"),
                rs.getLong("sale_item_id"),
                rs.getInt("quantity"),
                SqlMapperUtils.getBigDecimal(rs, "refund_amount"),
                rs.getLong("variant_id"),
                SqlMapperUtils.getBigDecimal(rs, "price_per_unit"),
                SqlMapperUtils.getBigDecimal(rs, "tax_rate"),
                rs.getString("variant_name"),
                rs.getString("sku"),
                rs.getString("product_name")
        );
    }
}

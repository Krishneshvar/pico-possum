package com.possum.persistence.mappers;

import com.possum.domain.model.PurchaseOrderItem;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class PurchaseOrderItemMapper implements RowMapper<PurchaseOrderItem> {
    @Override
    public PurchaseOrderItem map(ResultSet rs) throws SQLException {
        return new PurchaseOrderItem(
                rs.getLong("id"),
                rs.getLong("purchase_order_id"),
                rs.getLong("variant_id"),
                getOptionalColumn(rs, "variant_name"),
                getOptionalColumn(rs, "sku"),
                getOptionalColumn(rs, "product_name"),
                rs.getInt("quantity"),
                SqlMapperUtils.getBigDecimal(rs, "unit_cost")
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

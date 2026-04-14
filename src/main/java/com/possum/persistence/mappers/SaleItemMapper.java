package com.possum.persistence.mappers;

import com.possum.domain.model.SaleItem;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class SaleItemMapper implements RowMapper<SaleItem> {
    @Override
    public SaleItem map(ResultSet rs) throws SQLException {
        return new SaleItem(
                rs.getLong("id"),
                rs.getLong("sale_id"),
                rs.getLong("variant_id"),
                getOptionalColumn(rs, "variant_name"),
                getOptionalColumn(rs, "sku"),
                getOptionalColumn(rs, "product_name"),
                rs.getInt("quantity"),
                SqlMapperUtils.getBigDecimal(rs, "price_per_unit"),
                SqlMapperUtils.getBigDecimal(rs, "cost_per_unit"),
                SqlMapperUtils.getBigDecimal(rs, "tax_rate"),
                SqlMapperUtils.getBigDecimal(rs, "tax_amount"),
                SqlMapperUtils.getBigDecimal(rs, "applied_tax_rate"),
                SqlMapperUtils.getBigDecimal(rs, "applied_tax_amount"),
                getOptionalColumn(rs, "tax_rule_snapshot"),
                SqlMapperUtils.getBigDecimal(rs, "discount_amount"),
                getOptionalInt(rs, "returned_quantity")
        );
    }

    private static Integer getOptionalInt(ResultSet rs, String column) throws SQLException {
        try {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

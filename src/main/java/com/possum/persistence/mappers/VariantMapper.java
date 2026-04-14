package com.possum.persistence.mappers;

import com.possum.domain.model.Variant;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class VariantMapper implements RowMapper<Variant> {
    @Override
    public Variant map(ResultSet rs) throws SQLException {
        return new Variant(
                rs.getLong("id"),
                rs.getLong("product_id"),
                getOptionalColumn(rs, "product_name"),
                rs.getString("name"),
                rs.getString("sku"),
                SqlMapperUtils.getBigDecimal(rs, "price"),
                SqlMapperUtils.getBigDecimal(rs, "cost_price"),
                getNullableInt(rs, "stock_alert_cap"),
                SqlMapperUtils.getBooleanFromInt(rs, "is_default"),
                rs.getString("status"),
                getOptionalColumn(rs, "image_path"),
                getNullableInt(rs, "stock"),
                getOptionalColumn(rs, "category_name"),
                getOptionalColumn(rs, "tax_category_name"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
        );
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
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

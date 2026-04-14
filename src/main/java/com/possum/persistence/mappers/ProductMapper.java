package com.possum.persistence.mappers;

import com.possum.domain.model.Product;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class ProductMapper implements RowMapper<Product> {
    @Override
    public Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                getNullableLong(rs, "category_id"),
                getOptionalColumn(rs, "category_name"),
                getNullableLong(rs, "tax_category_id"),
                getOptionalColumn(rs, "tax_category_name"),
                rs.getString("status"),
                getOptionalColumn(rs, "image_path"),
                getNullableInt(rs, "stock"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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

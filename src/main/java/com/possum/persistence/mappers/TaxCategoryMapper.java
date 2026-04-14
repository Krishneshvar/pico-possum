package com.possum.persistence.mappers;

import com.possum.domain.model.TaxCategory;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TaxCategoryMapper implements RowMapper<TaxCategory> {
    @Override
    public TaxCategory map(ResultSet rs) throws SQLException {
        return new TaxCategory(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                getOptionalInt(rs, "product_count"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at")
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
}

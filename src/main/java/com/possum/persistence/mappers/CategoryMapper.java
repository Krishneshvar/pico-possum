package com.possum.persistence.mappers;

import com.possum.domain.model.Category;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class CategoryMapper implements RowMapper<Category> {
    @Override
    public Category map(ResultSet rs) throws SQLException {
        return new Category(
                rs.getLong("id"),
                rs.getString("name"),
                getNullableLong(rs, "parent_id"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}

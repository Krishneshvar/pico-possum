package com.possum.persistence.mappers;

import com.possum.domain.model.User;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class UserMapper implements RowMapper<User> {
    @Override
    public User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("username"),
                rs.getString("password_hash"),
                SqlMapperUtils.getBooleanFromInt(rs, "is_active"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
        );
    }
}

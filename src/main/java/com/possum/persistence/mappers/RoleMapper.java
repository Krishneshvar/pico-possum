package com.possum.persistence.mappers;

import com.possum.domain.model.Role;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class RoleMapper implements RowMapper<Role> {
    @Override
    public Role map(ResultSet rs) throws SQLException {
        return new Role(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description")
        );
    }
}

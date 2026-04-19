package com.picopossum.persistence.mappers;

import com.picopossum.domain.model.Permission;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class PermissionMapper implements RowMapper<Permission> {
    @Override
    public Permission map(ResultSet rs) throws SQLException {
        return new Permission(
                rs.getLong("id"),
                rs.getString("key"),
                rs.getString("description")
        );
    }
}

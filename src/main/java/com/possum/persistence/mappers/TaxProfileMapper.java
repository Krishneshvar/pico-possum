package com.possum.persistence.mappers;

import com.possum.domain.model.TaxProfile;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TaxProfileMapper implements RowMapper<TaxProfile> {
    @Override
    public TaxProfile map(ResultSet rs) throws SQLException {
        return new TaxProfile(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("country_code"),
                rs.getString("region_code"),
                rs.getString("pricing_mode"),
                SqlMapperUtils.getBooleanFromInt(rs, "is_active"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at")
        );
    }
}

package com.possum.persistence.mappers;

import com.possum.domain.model.TaxRule;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TaxRuleMapper implements RowMapper<TaxRule> {
    @Override
    public TaxRule map(ResultSet rs) throws SQLException {
        return new TaxRule(
                rs.getLong("id"),
                rs.getLong("tax_profile_id"),
                getNullableLong(rs, "tax_category_id"),
                rs.getString("rule_scope"),
                SqlMapperUtils.getBigDecimal(rs, "min_price"),
                SqlMapperUtils.getBigDecimal(rs, "max_price"),
                SqlMapperUtils.getBigDecimal(rs, "min_invoice_total"),
                SqlMapperUtils.getBigDecimal(rs, "max_invoice_total"),
                rs.getString("customer_type"),
                SqlMapperUtils.getBigDecimal(rs, "rate_percent"),
                SqlMapperUtils.getBooleanFromInt(rs, "is_compound"),
                rs.getInt("priority"),
                SqlMapperUtils.getLocalDate(rs, "valid_from"),
                SqlMapperUtils.getLocalDate(rs, "valid_to"),
                getOptionalColumn(rs, "category_name"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

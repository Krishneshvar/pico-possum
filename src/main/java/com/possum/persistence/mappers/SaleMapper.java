package com.possum.persistence.mappers;

import com.possum.domain.model.Sale;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class SaleMapper implements RowMapper<Sale> {
    @Override
    public Sale map(ResultSet rs) throws SQLException {
        return new Sale(
                rs.getLong("id"),
                rs.getString("invoice_number"),
                SqlMapperUtils.getLocalDateTime(rs, "sale_date"),
                SqlMapperUtils.getBigDecimal(rs, "total_amount"),
                SqlMapperUtils.getBigDecimal(rs, "paid_amount"),
                SqlMapperUtils.getBigDecimal(rs, "discount"),
                SqlMapperUtils.getBigDecimal(rs, "total_tax"),
                rs.getString("status"),
                rs.getString("fulfillment_status"),
                getNullableLong(rs, "customer_id"),
                getNullableLong(rs, "user_id"),
                getOptionalColumn(rs, "customer_name"),
                getOptionalColumn(rs, "customer_phone"),
                getOptionalColumn(rs, "customer_email"),
                getOptionalColumn(rs, "biller_name"),
                getNullableLong(rs, "payment_method_id"),
                getOptionalColumn(rs, "payment_method_name")
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

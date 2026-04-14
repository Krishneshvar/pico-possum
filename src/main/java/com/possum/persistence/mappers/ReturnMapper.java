package com.possum.persistence.mappers;

import com.possum.domain.model.Return;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class ReturnMapper implements RowMapper<Return> {
    @Override
    public Return map(ResultSet rs) throws SQLException {
        long pmId = rs.getLong("payment_method_id");
        Long paymentMethodId = rs.wasNull() ? null : pmId;

        return new Return(
                rs.getLong("id"),
                rs.getLong("sale_id"),
                rs.getLong("user_id"),
                rs.getString("reason"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                getOptionalColumn(rs, "invoice_number"),
                getOptionalColumn(rs, "processed_by_name"),
                SqlMapperUtils.getBigDecimal(rs, "total_refund"),
                paymentMethodId,
                getOptionalColumn(rs, "payment_method_name")
        );
    }

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

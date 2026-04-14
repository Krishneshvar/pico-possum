package com.possum.persistence.mappers;

import com.possum.domain.model.Transaction;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class TransactionMapper implements RowMapper<Transaction> {
    @Override
    public Transaction map(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getLong("id"),
                SqlMapperUtils.getBigDecimal(rs, "amount"),
                rs.getString("type"),
                rs.getLong("payment_method_id"),
                getOptionalColumn(rs, "payment_method_name"),
                rs.getString("status"),
                SqlMapperUtils.getLocalDateTime(rs, "transaction_date"),
                getOptionalColumn(rs, "invoice_number"),
                getOptionalColumn(rs, "customer_name"),
                getOptionalColumn(rs, "supplier_name")
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

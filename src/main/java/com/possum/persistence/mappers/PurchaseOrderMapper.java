package com.possum.persistence.mappers;

import com.possum.domain.model.PurchaseOrder;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class PurchaseOrderMapper implements RowMapper<PurchaseOrder> {
    @Override
    public PurchaseOrder map(ResultSet rs) throws SQLException {
        return new PurchaseOrder(
                rs.getLong("id"),
                getOptionalColumn(rs, "invoice_number"),
                rs.getLong("supplier_id"),
                getOptionalColumn(rs, "supplier_name"),
                rs.getLong("payment_method_id"),
                getOptionalColumn(rs, "payment_method_name"),
                rs.getString("status"),
                SqlMapperUtils.getLocalDateTime(rs, "order_date"),
                SqlMapperUtils.getLocalDateTime(rs, "received_date"),
                rs.getLong("created_by"),
                getOptionalColumn(rs, "created_by_name"),
                getOptionalInt(rs, "item_count"),
                SqlMapperUtils.getBigDecimal(rs, "total_cost")
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

    private static String getOptionalColumn(ResultSet rs, String column) throws SQLException {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}

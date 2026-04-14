package com.possum.persistence.mappers;

import com.possum.domain.model.Customer;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class CustomerMapper implements RowMapper<Customer> {
    @Override
    public Customer map(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getString("customer_type"),
                rs.getInt("is_tax_exempt") == 1,
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at"),
                SqlMapperUtils.getLocalDateTime(rs, "deleted_at")
        );
    }
}

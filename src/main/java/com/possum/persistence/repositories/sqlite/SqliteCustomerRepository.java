package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Customer;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.CustomerMapper;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.PagedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteCustomerRepository extends BaseSqliteRepository implements CustomerRepository {

    private final CustomerMapper mapper = new CustomerMapper();

    public SqliteCustomerRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public PagedResult<Customer> findCustomers(CustomerFilter filter) {
        WhereBuilder whereBuilder = new WhereBuilder()
                .addNotDeleted()
                .addSearch(filter.searchTerm(), "name", "email", "phone");

        String whereClause = whereBuilder.build();
        List<Object> params = new ArrayList<>(whereBuilder.getParams());

        int safePageInput = filter.page() != null ? filter.page() : filter.currentPage();
        int safeLimitInput = filter.limit() != null ? filter.limit() : filter.itemsPerPage();
        int safePage = Math.max(1, safePageInput);
        int safeLimit = Math.max(1, Math.min(1000, safeLimitInput));
        int offset = (safePage - 1) * safeLimit;

        int totalCount = count("customers", whereClause, params.toArray());

        String sortField = switch (filter.sortBy() == null ? "name" : filter.sortBy()) {
            case "email" -> "email";
            case "created_at" -> "created_at";
            default -> "name";
        };
        String sortOrder = "DESC".equalsIgnoreCase(filter.sortOrder()) ? "DESC" : "ASC";

        params.add(safeLimit);
        params.add(offset);
        List<Customer> rows = queryList(
                """
                SELECT
                  id, name, phone, email, address, customer_type, is_tax_exempt, created_at, updated_at, deleted_at
                FROM customers
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(whereClause, sortField, sortOrder),
                mapper,
                params.toArray()
        );

        int totalPages = totalCount > 0 ? (int) Math.ceil((double) totalCount / safeLimit) : 1;
        return new PagedResult<>(rows, totalCount, totalPages, safePage, safeLimit);
    }

    @Override
    public Optional<Customer> findCustomerById(long id) {
        return queryOne(
                """
                SELECT id, name, phone, email, address, customer_type, is_tax_exempt, created_at, updated_at, deleted_at
                FROM customers
                WHERE id = ? AND deleted_at IS NULL
                """,
                mapper,
                id
        );
    }

    @Override
    public Optional<Customer> insertCustomer(String name, String phone, String email, String address,
                                              String customerType, Boolean isTaxExempt) {
        long id = executeInsert(
                """
                INSERT INTO customers (name, phone, email, address, customer_type, is_tax_exempt)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                name, phone, email, address,
                customerType,
                Boolean.TRUE.equals(isTaxExempt) ? 1 : 0
        );
        return findCustomerById(id);
    }

    @Override
    public Optional<Customer> updateCustomerById(long id, String name, String phone, String email, String address,
                                                  String customerType, Boolean isTaxExempt) {
        UpdateBuilder builder = new UpdateBuilder("customers")
                .set("name", name)
                .set("phone", phone)
                .set("email", email)
                .set("address", address)
                .set("customer_type", customerType)
                .set("is_tax_exempt", Boolean.TRUE.equals(isTaxExempt) ? 1 : 0)
                .where("id = ? AND deleted_at IS NULL", id);

        if (builder.hasFields()) {
            executeUpdate(builder.getSql(), builder.getParams());
        }

        return findCustomerById(id);
    }

    @Override
    public boolean softDeleteCustomer(long id) {
        return softDelete("customers", id) > 0;
    }
}

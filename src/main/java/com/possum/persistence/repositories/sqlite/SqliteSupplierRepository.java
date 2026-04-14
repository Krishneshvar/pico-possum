package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.model.Supplier;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SupplierFilter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteSupplierRepository extends BaseSqliteRepository implements SupplierRepository {

    public SqliteSupplierRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public PagedResult<Supplier> getAllSuppliers(SupplierFilter filter) {
        WhereBuilder whereBuilder = new WhereBuilder()
                .addNotDeleted("s")
                .addSearch(filter.searchTerm(), "s.name", "s.contact_person", "s.email", "s.phone", "s.address", "s.gstin")
                .addIn("s.payment_policy_id", filter.paymentPolicyIds());

        String where = whereBuilder.build();
        List<Object> params = new ArrayList<>(whereBuilder.getParams());

        int total = count("suppliers s", where, params.toArray());

        String sortBy = switch (filter.sortBy() == null ? "name" : filter.sortBy()) {
            case "contact_person" -> "s.contact_person";
            case "phone" -> "s.phone";
            case "email" -> "s.email";
            case "created_at" -> "s.created_at";
            default -> "s.name";
        };
        String sortOrder = "DESC".equalsIgnoreCase(filter.sortOrder()) ? "DESC" : "ASC";
        int page = Math.max(1, filter.page());
        int limit = Math.max(1, filter.limit());
        int offset = (page - 1) * limit;

        params.add(limit);
        params.add(offset);

        List<Supplier> suppliers = queryList(
                """
                SELECT
                  s.id, s.name, s.contact_person, s.phone, s.email, s.address, s.gstin,
                  s.payment_policy_id, s.created_at, s.updated_at, s.deleted_at,
                  pp.name AS payment_policy_name
                FROM suppliers s
                LEFT JOIN payment_policies pp ON s.payment_policy_id = pp.id
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(where, sortBy, sortOrder),
                this::mapSupplier,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(suppliers, total, totalPages, page, limit);
    }

    @Override
    public Optional<Supplier> findSupplierById(long id) {
        return queryOne(
                """
                SELECT
                  s.id, s.name, s.contact_person, s.phone, s.email, s.address, s.gstin,
                  s.payment_policy_id, s.created_at, s.updated_at, s.deleted_at,
                  pp.name AS payment_policy_name
                FROM suppliers s
                LEFT JOIN payment_policies pp ON s.payment_policy_id = pp.id
                WHERE s.id = ? AND s.deleted_at IS NULL
                """,
                this::mapSupplier,
                id
        );
    }

    @Override
    public long createSupplier(Supplier supplier) {
        return executeInsert(
                """
                INSERT INTO suppliers (name, contact_person, phone, email, address, gstin, payment_policy_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                supplier.name(),
                supplier.contactPerson(),
                supplier.phone(),
                supplier.email(),
                supplier.address(),
                supplier.gstin(),
                supplier.paymentPolicyId() == null ? 1L : supplier.paymentPolicyId()
        );
    }

    @Override
    public int updateSupplier(long id, Supplier supplier) {
        UpdateBuilder builder = new UpdateBuilder("suppliers")
                .set("name", supplier.name())
                .set("contact_person", supplier.contactPerson())
                .set("phone", supplier.phone())
                .set("email", supplier.email())
                .set("address", supplier.address())
                .set("gstin", supplier.gstin())
                .set("payment_policy_id", supplier.paymentPolicyId())
                .where("id = ? AND deleted_at IS NULL", id);

        return executeUpdate(builder.getSql(), builder.getParams());
    }

    @Override
    public int deleteSupplier(long id) {
        return softDelete("suppliers", id);
    }

    @Override
    public List<PaymentPolicy> getPaymentPolicies() {
        return queryList(
                """
                SELECT id, name, days_to_pay, description, created_at, updated_at, deleted_at
                FROM payment_policies
                WHERE deleted_at IS NULL
                ORDER BY name ASC
                """,
                this::mapPaymentPolicy
        );
    }

    @Override
    public long createPaymentPolicy(String name, int daysToPay, String description) {
        return executeInsert(
                "INSERT INTO payment_policies (name, days_to_pay, description) VALUES (?, ?, ?)",
                name,
                daysToPay,
                description
        );
    }

    @Override
    public int updatePaymentPolicy(long id, String name, int daysToPay, String description) {
        return executeUpdate(
                "UPDATE payment_policies SET name = ?, days_to_pay = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL",
                name,
                daysToPay,
                description,
                id
        );
    }

    @Override
    public int deletePaymentPolicy(long id) {
        return softDelete("payment_policies", id);
    }

    private Supplier mapSupplier(ResultSet rs) throws SQLException {
        return new Supplier(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("contact_person"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address"),
                rs.getString("gstin"),
                rs.getLong("payment_policy_id"),
                rs.getString("payment_policy_name"),
                parseDateTime(rs.getString("created_at")),
                parseDateTime(rs.getString("updated_at")),
                parseDateTime(rs.getString("deleted_at"))
        );
    }

    private PaymentPolicy mapPaymentPolicy(ResultSet rs) throws SQLException {
        return new PaymentPolicy(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getInt("days_to_pay"),
                rs.getString("description"),
                parseDateTime(rs.getString("created_at")),
                parseDateTime(rs.getString("updated_at")),
                parseDateTime(rs.getString("deleted_at"))
        );
    }
}

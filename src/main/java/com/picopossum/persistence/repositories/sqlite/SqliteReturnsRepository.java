package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Return;
import com.picopossum.domain.model.ReturnItem;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.ReturnItemMapper;
import com.picopossum.persistence.mappers.ReturnMapper;
import com.picopossum.domain.repositories.ReturnsRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteReturnsRepository extends BaseSqliteRepository implements ReturnsRepository {

    private final ReturnMapper returnMapper = new ReturnMapper();
    private final ReturnItemMapper returnItemMapper = new ReturnItemMapper();

    public SqliteReturnsRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertReturn(Return returnRecord) {
        return executeInsert(
                "INSERT INTO returns (sale_id, user_id, reason, refund_amount, payment_method_id) VALUES (?, ?, ?, ?, ?)",
                returnRecord.saleId(),
                returnRecord.userId(),
                returnRecord.reason(),
                returnRecord.totalRefund(),
                returnRecord.paymentMethodId()
        );
    }

    @Override
    public long insertReturnItem(ReturnItem item) {
        return executeInsert(
                "INSERT INTO return_items (return_id, sale_item_id, quantity, refund_amount) VALUES (?, ?, ?, ?)",
                item.returnId(),
                item.saleItemId(),
                item.quantity(),
                item.refundAmount()
        );
    }

    @Override
    public Optional<Return> findReturnById(long id) {
        return queryOne(
                """
                SELECT
                  r.*,
                  s.invoice_number,
                  u.name AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                JOIN users u ON r.user_id = u.id
                LEFT JOIN payment_methods pm ON r.payment_method_id = pm.id
                WHERE r.id = ?
                GROUP BY r.id
                """,
                returnMapper,
                id
        );
    }

    @Override
    public List<ReturnItem> findReturnItems(long returnId) {
        return queryList(
                """
                SELECT
                  ri.*,
                  si.product_id,
                  si.price_per_unit,
                  p.sku,
                  p.name AS product_name
                FROM return_items ri
                JOIN sale_items si ON ri.sale_item_id = si.id
                JOIN products p ON si.product_id = p.id
                WHERE ri.return_id = ?
                ORDER BY ri.id ASC
                """,
                returnItemMapper,
                returnId
        );
    }

    @Override
    public PagedResult<Return> findReturns(ReturnFilter filter) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(filter, params);
        int page = Math.max(1, filter.currentPage());
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        int total = queryOne(
                """
                SELECT COUNT(*) AS count
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                %s
                """.formatted(where),
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortBy = "total_refund".equalsIgnoreCase(filter.sortBy()) ? "total_refund" : "r.created_at";
        String sortOrder = "ASC".equalsIgnoreCase(filter.sortOrder()) ? "ASC" : "DESC";
        params.add(limit);
        params.add(offset);

        List<Return> rows = queryList(
                """
                SELECT
                  r.*,
                  s.invoice_number,
                  u.name AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                JOIN users u ON r.user_id = u.id
                LEFT JOIN payment_methods pm ON r.payment_method_id = pm.id
                %s
                GROUP BY r.id
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(where, sortBy, sortOrder),
                returnMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(rows, total, totalPages, page, limit);
    }

    @Override
    public int getTotalReturnedQuantity(long saleItemId) {
        return queryOne(
                "SELECT COALESCE(SUM(quantity), 0) AS total_returned FROM return_items WHERE sale_item_id = ?",
                rs -> rs.getInt("total_returned"),
                saleItemId
        ).orElse(0);
    }

    private static String buildWhere(ReturnFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        if (filter.saleId() != null) {
            joiner.add("r.sale_id = ?");
            params.add(filter.saleId());
        }
        if (filter.userId() != null) {
            joiner.add("r.user_id = ?");
            params.add(filter.userId());
        }
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            String date = filter.startDate().substring(0, Math.min(10, filter.startDate().length()));
            joiner.add("r.created_at >= ?");
            params.add(date + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            String date = filter.endDate().substring(0, Math.min(10, filter.endDate().length()));
            joiner.add("r.created_at <= ?");
            params.add(date + " 23:59:59");
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            String fuzzy = "%" + filter.searchTerm() + "%";
            joiner.add("(CAST(r.id AS TEXT) LIKE ? OR s.invoice_number LIKE ? OR COALESCE(r.reason, '') LIKE ?)");
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }

        String refundSub = "(SELECT COALESCE(SUM(ri.refund_amount), 0) FROM return_items ri WHERE ri.return_id = r.id)";
        if (filter.minAmount() != null) {
            joiner.add(refundSub + " >= ?");
            params.add(filter.minAmount().doubleValue());
        }
        if (filter.maxAmount() != null) {
            joiner.add(refundSub + " <= ?");
            params.add(filter.maxAmount().doubleValue());
        }

        if (filter.paymentMethodIds() != null && !filter.paymentMethodIds().isEmpty()) {
            joiner.add("r.payment_method_id IN (" 
                    + "?,".repeat(filter.paymentMethodIds().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.paymentMethodIds());
        }

        if (joiner.length() == 0) {
            return "";
        }
        return "WHERE " + joiner;
    }

    @Override
    public List<Return> findReturnsBySaleId(long saleId) {
        return queryList(
                """
                SELECT
                  r.*,
                  s.invoice_number,
                  u.name AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                JOIN users u ON r.user_id = u.id
                LEFT JOIN payment_methods pm ON r.payment_method_id = pm.id
                WHERE r.sale_id = ?
                GROUP BY r.id
                ORDER BY r.created_at DESC
                """,
                returnMapper,
                saleId
        );
    }
}

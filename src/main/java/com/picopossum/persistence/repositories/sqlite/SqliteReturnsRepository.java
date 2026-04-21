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
                "INSERT INTO returns (sale_id, reason, refund_amount, payment_method_id, invoice_id) VALUES (?, ?, ?, ?, ?)",
                returnRecord.saleId(),
                returnRecord.reason(),
                returnRecord.totalRefund(),
                returnRecord.paymentMethodId(),
                returnRecord.invoiceId()
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
                  'System Admin' AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name,
                  r.invoice_id
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
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
        WhereBuilder whereBuilder = new WhereBuilder();
        
        if (filter.saleId() != null) {
            whereBuilder.addCondition("r.sale_id = ?", filter.saleId());
        }
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            String date = filter.startDate().substring(0, Math.min(10, filter.startDate().length()));
            whereBuilder.addCondition("r.created_at >= ?", date + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            String date = filter.endDate().substring(0, Math.min(10, filter.endDate().length()));
            whereBuilder.addCondition("r.created_at <= ?", date + " 23:59:59");
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            whereBuilder.addSearch(filter.searchTerm(), "r.id", "s.invoice_number", "r.invoice_id", "r.reason");
        }
        if (filter.minAmount() != null) {
            whereBuilder.addCondition("r.refund_amount >= ?", filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            whereBuilder.addCondition("r.refund_amount <= ?", filter.maxAmount());
        }
        if (filter.paymentMethodIds() != null && !filter.paymentMethodIds().isEmpty()) {
            whereBuilder.addIn("r.payment_method_id", filter.paymentMethodIds());
        }

        String whereClause = whereBuilder.build();
        List<Object> params = new ArrayList<>(whereBuilder.getParams());

        int page = Math.max(1, filter.currentPage());
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        int total = queryOne(
                """
                SELECT COUNT(*) AS count
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                %s
                """.formatted(whereClause),
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
                  'System Admin' AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name,
                  r.invoice_id
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
                LEFT JOIN payment_methods pm ON r.payment_method_id = pm.id
                %s
                GROUP BY r.id
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(whereClause, sortBy, sortOrder),
                returnMapper,
                params.toArray()
        );

        int totalPages = total > 0 ? (int) Math.ceil((double) total / limit) : 1;
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



    @Override
    public List<Return> findReturnsBySaleId(long saleId) {
        return queryList(
                """
                SELECT
                  r.*,
                  s.invoice_number,
                  'System Admin' AS processed_by_name,
                  r.refund_amount AS total_refund,
                  r.payment_method_id,
                  pm.name AS payment_method_name,
                  r.invoice_id
                FROM returns r
                JOIN sales s ON r.sale_id = s.id
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

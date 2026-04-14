package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Transaction;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.TransactionMapper;
import com.possum.domain.repositories.TransactionRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public final class SqliteTransactionRepository extends BaseSqliteRepository implements TransactionRepository {

    private static final Set<String> SORTABLE = Set.of(
            "transaction_date", "amount", "status", "customer_name", "invoice_number", "supplier_name", "type"
    );
    private static final String UNIFIED_TRANSACTIONS_CTE = """
            WITH unified_transactions AS (
              SELECT
                t.id AS id,
                t.amount AS amount,
                t.type AS type,
                t.payment_method_id AS payment_method_id,
                pm.name AS payment_method_name,
                t.status AS status,
                t.transaction_date AS transaction_date,
                COALESCE(s.invoice_number, po.invoice_number) AS invoice_number,
                c.name AS customer_name,
                sup.name AS supplier_name
              FROM transactions t
              LEFT JOIN payment_methods pm ON t.payment_method_id = pm.id
              LEFT JOIN sales s ON t.sale_id = s.id
              LEFT JOIN customers c ON s.customer_id = c.id
              LEFT JOIN purchase_orders po ON t.purchase_order_id = po.id
              LEFT JOIN suppliers sup ON po.supplier_id = sup.id

              UNION ALL

              SELECT
                -ls.id AS id,
                ls.net_amount AS amount,
                'payment' AS type,
                ls.payment_method_id AS payment_method_id,
                COALESCE(NULLIF(trim(ls.payment_method_name), ''), 'Legacy Import') AS payment_method_name,
                'legacy' AS status,
                ls.sale_date AS transaction_date,
                ls.invoice_number AS invoice_number,
                CASE WHEN ls.customer_name IS NULL OR trim(ls.customer_name) = '' THEN 'Walk-in Customer' ELSE ls.customer_name END AS customer_name,
                NULL AS supplier_name
              FROM legacy_sales ls
            )
            """;

    private final TransactionMapper mapper = new TransactionMapper();

    public SqliteTransactionRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public PagedResult<Transaction> findTransactions(TransactionFilter filter) {
        List<Object> params = new ArrayList<>();
        String where = buildUnifiedWhere(filter, params);

        int totalCount = queryOne(
                """
                %s
                SELECT COUNT(*) AS count
                FROM unified_transactions ut
                %s
                """.formatted(UNIFIED_TRANSACTIONS_CTE, where),
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortBy = SORTABLE.contains(filter.sortBy()) ? filter.sortBy() : "transaction_date";
        String sortExpr = switch (sortBy) {
            case "amount" -> "ABS(ut.amount)";
            case "status" -> "ut.status";
            case "customer_name" -> "ut.customer_name";
            case "invoice_number" -> "ut.invoice_number";
            case "supplier_name" -> "ut.supplier_name";
            case "type" -> "ut.type";
            default -> "ut.transaction_date";
        };
        String sortOrder = "ASC".equalsIgnoreCase(filter.sortOrder()) ? "ASC" : "DESC";
        int page = Math.max(1, filter.currentPage());
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        params.add(limit);
        params.add(offset);

        List<Transaction> transactions = queryList(
                """
                %s
                SELECT
                  ut.*
                FROM unified_transactions ut
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(UNIFIED_TRANSACTIONS_CTE, where, sortExpr, sortOrder),
                mapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) totalCount / limit);
        return new PagedResult<>(transactions, totalCount, totalPages, page, limit);
    }

    @Override
    public Optional<Transaction> findTransactionById(long id) {
        return queryOne(
                """
                %s
                SELECT
                  ut.*
                FROM unified_transactions ut
                WHERE ut.id = ?
                """.formatted(UNIFIED_TRANSACTIONS_CTE),
                mapper,
                id
        );
    }

    @Override
    public List<Transaction> findTransactionsByPurchaseOrderId(long purchaseOrderId) {
        return queryList(
                """
                SELECT
                  t.*,
                  pm.name AS payment_method_name,
                  COALESCE(s.invoice_number, po.invoice_number) AS invoice_number,
                  c.name AS customer_name,
                  sup.name AS supplier_name
                FROM transactions t
                LEFT JOIN payment_methods pm ON t.payment_method_id = pm.id
                LEFT JOIN sales s ON t.sale_id = s.id
                LEFT JOIN customers c ON s.customer_id = c.id
                LEFT JOIN purchase_orders po ON t.purchase_order_id = po.id
                LEFT JOIN suppliers sup ON po.supplier_id = sup.id
                WHERE t.purchase_order_id = ?
                ORDER BY t.transaction_date DESC
                """,
                mapper,
                purchaseOrderId
        );
    }

    @Override
    public long insertTransaction(Transaction transaction, Long saleId, Long purchaseOrderId) {
        return executeInsert(
                """
                INSERT INTO transactions (
                    sale_id, purchase_order_id, amount, type,
                    payment_method_id, status, transaction_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                saleId,
                purchaseOrderId,
                transaction.amount(),
                transaction.type(),
                transaction.paymentMethodId(),
                transaction.status(),
                transaction.transactionDate()
        );
    }

    @Override
    public BigDecimal getTotalRefundedForSale(long saleId) {
        return queryOne(
                """
                SELECT COALESCE(SUM(ABS(amount)), 0) AS total
                FROM transactions
                WHERE sale_id = ? AND type = 'refund' AND status = 'completed'
                """,
                rs -> rs.getBigDecimal("total"),
                saleId
        ).orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalPaidForSale(long saleId) {
        return queryOne(
                """
                SELECT COALESCE(SUM(amount), 0) AS total
                FROM transactions
                WHERE sale_id = ? AND type = 'payment' AND status = 'completed'
                """,
                rs -> rs.getBigDecimal("total"),
                saleId
        ).orElse(BigDecimal.ZERO);
    }

    private static String buildUnifiedWhere(TransactionFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            String date = filter.startDate().substring(0, Math.min(10, filter.startDate().length()));
            joiner.add("ut.transaction_date >= ?");
            params.add(date + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            String date = filter.endDate().substring(0, Math.min(10, filter.endDate().length()));
            joiner.add("ut.transaction_date <= ?");
            params.add(date + " 23:59:59");
        }
        if (filter.type() != null && !filter.type().isEmpty()) {
            joiner.add("ut.type IN (" + "?,".repeat(filter.type().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.type());
        }
        if (filter.paymentMethodId() != null && !filter.paymentMethodId().isEmpty()) {
            joiner.add("ut.payment_method_id IN (" + "?,".repeat(filter.paymentMethodId().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.paymentMethodId());
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            joiner.add("ut.status = ?");
            params.add(filter.status());
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            String fuzzy = "%" + filter.searchTerm().trim() + "%";
            joiner.add("(COALESCE(ut.invoice_number, '') LIKE ? OR COALESCE(ut.customer_name, '') LIKE ? OR COALESCE(ut.supplier_name, '') LIKE ?)");
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (filter.minAmount() != null) {
            joiner.add("ABS(ut.amount) >= ?");
            params.add(filter.minAmount().doubleValue());
        }
        if (filter.maxAmount() != null) {
            joiner.add("ABS(ut.amount) <= ?");
            params.add(filter.maxAmount().doubleValue());
        }
        if (joiner.length() == 0) {
            return "";
        }
        return "WHERE " + joiner;
    }
}

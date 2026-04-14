package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.LegacySale;
import com.possum.domain.model.PaymentMethod;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.SaleItemMapper;
import com.possum.persistence.mappers.SaleMapper;
import com.possum.persistence.mappers.TransactionMapper;
import com.possum.domain.repositories.SalesRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SaleFilter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public final class SqliteSalesRepository extends BaseSqliteRepository implements SalesRepository {

    private static final Set<String> SORTABLE = Set.of(
            "sale_date", "total_amount", "invoice_number", "paid_amount", "status", "fulfillment_status", "customer_name"
    );
    private static final String UNIFIED_SALES_CTE = """
            WITH unified_sales AS (
              SELECT
                s.id AS id,
                s.invoice_number AS invoice_number,
                s.sale_date AS sale_date,
                s.total_amount AS total_amount,
                s.paid_amount AS paid_amount,
                s.discount AS discount,
                s.total_tax AS total_tax,
                s.status AS status,
                s.fulfillment_status AS fulfillment_status,
                s.customer_id AS customer_id,
                s.user_id AS user_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                c.email AS customer_email,
                u.name AS biller_name,
                (SELECT t.payment_method_id FROM transactions t WHERE t.sale_id = s.id AND t.status = 'completed' LIMIT 1) AS payment_method_id,
                (SELECT GROUP_CONCAT(DISTINCT pm.name) FROM transactions t JOIN payment_methods pm ON t.payment_method_id = pm.id WHERE t.sale_id = s.id AND t.status = 'completed') AS payment_method_name
              FROM sales s
              LEFT JOIN customers c ON s.customer_id = c.id
              LEFT JOIN users u ON s.user_id = u.id

              UNION ALL

              SELECT
                -ls.id AS id,
                ls.invoice_number AS invoice_number,
                ls.sale_date AS sale_date,
                ls.net_amount AS total_amount,
                ls.net_amount AS paid_amount,
                0 AS discount,
                0 AS total_tax,
                'legacy' AS status,
                'fulfilled' AS fulfillment_status,
                NULL AS customer_id,
                NULL AS user_id,
                CASE WHEN ls.customer_name IS NULL OR trim(ls.customer_name) = '' THEN 'Walk-in Customer' ELSE ls.customer_name END AS customer_name,
                NULL AS customer_phone,
                NULL AS customer_email,
                'Legacy Import' AS biller_name,
                ls.payment_method_id AS payment_method_id,
                COALESCE(NULLIF(trim(ls.payment_method_name), ''), 'Legacy Import') AS payment_method_name
              FROM legacy_sales ls
            )
            """;

    private final SaleMapper saleMapper = new SaleMapper();
    private final SaleItemMapper saleItemMapper = new SaleItemMapper();
    private final TransactionMapper transactionMapper = new TransactionMapper();

    public SqliteSalesRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertSale(Sale sale) {
        return executeInsert(
                """
                INSERT INTO sales (
                  invoice_number, total_amount, paid_amount, discount, total_tax, status, fulfillment_status, customer_id, user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sale.invoiceNumber(),
                sale.totalAmount(),
                sale.paidAmount(),
                sale.discount(),
                sale.totalTax(),
                sale.status(),
                sale.fulfillmentStatus() == null ? "pending" : sale.fulfillmentStatus(),
                sale.customerId(),
                sale.userId()
        );
    }

    @Override
    public long insertSaleItem(SaleItem item) {
        return executeInsert(
                """
                INSERT INTO sale_items (
                  sale_id, variant_id, quantity, price_per_unit, cost_per_unit, tax_rate, tax_amount, discount_amount,
                  applied_tax_rate, applied_tax_amount, tax_rule_snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                item.saleId(),
                item.variantId(),
                item.quantity(),
                item.pricePerUnit(),
                item.costPerUnit(),
                item.taxRate(),
                item.taxAmount(),
                item.discountAmount(),
                item.appliedTaxRate(),
                item.appliedTaxAmount(),
                item.taxRuleSnapshot()
        );
    }

    @Override
    public Optional<Sale> findSaleById(long id) {
        return queryOne(
                """
                SELECT
                  s.*, c.name AS customer_name, c.phone AS customer_phone, c.email AS customer_email, u.name AS biller_name,
                  t.payment_method_id,
                  pm.name AS payment_method_name
                FROM sales s
                LEFT JOIN customers c ON s.customer_id = c.id
                LEFT JOIN users u ON s.user_id = u.id
                LEFT JOIN transactions t ON t.sale_id = s.id AND t.type = 'payment'
                LEFT JOIN payment_methods pm ON t.payment_method_id = pm.id
                WHERE s.id = ?
                GROUP BY s.id
                """,
                saleMapper,
                id
        );
    }

    @Override
    public Optional<Sale> findSaleByInvoiceNumber(String invoiceNumber) {
        String query = """
                SELECT
                  s.*, c.name AS customer_name, c.phone AS customer_phone, c.email AS customer_email, u.name AS biller_name,
                  t.payment_method_id,
                  pm.name AS payment_method_name
                FROM sales s
                LEFT JOIN customers c ON s.customer_id = c.id
                LEFT JOIN users u ON s.user_id = u.id
                LEFT JOIN transactions t ON t.sale_id = s.id AND t.type = 'payment'
                LEFT JOIN payment_methods pm ON t.payment_method_id = pm.id
                WHERE s.invoice_number = ?
                GROUP BY s.id
                """;
        
        Optional<Sale> result = queryOne(query, saleMapper, invoiceNumber);
        if (result.isPresent()) return result;

        // Stabilization: Also try finding by trailing sequence if the input is numeric
        if (invoiceNumber != null && invoiceNumber.matches("\\d+")) {
            String fallbackQuery = query.replace("s.invoice_number = ?", "s.invoice_number LIKE ?")
                                      .replace("GROUP BY s.id", "GROUP BY s.id ORDER BY s.id DESC LIMIT 1");
            return queryOne(fallbackQuery, saleMapper, "%" + invoiceNumber);
        }
        
        return Optional.empty();
    }

    @Override
    public List<SaleItem> findSaleItems(long saleId) {
        return queryList(
                """
                SELECT
                  si.*, v.name AS variant_name, v.sku, p.name AS product_name,
                  (SELECT COALESCE(SUM(ri.quantity), 0) FROM return_items ri WHERE ri.sale_item_id = si.id) AS returned_quantity
                FROM sale_items si
                JOIN variants v ON si.variant_id = v.id
                JOIN products p ON v.product_id = p.id
                WHERE si.sale_id = ?
                """,
                saleItemMapper,
                saleId
        );
    }

    @Override
    public List<Transaction> findTransactionsBySaleId(long saleId) {
        return queryList(
                """
                SELECT t.*, pm.name AS payment_method_name
                FROM transactions t
                JOIN payment_methods pm ON t.payment_method_id = pm.id
                WHERE t.sale_id = ?
                ORDER BY t.transaction_date ASC
                """,
                transactionMapper,
                saleId
        );
    }

    @Override
    public PagedResult<Sale> findSales(SaleFilter filter) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildUnifiedWhere(filter, params);

        int total = queryOne(
                """
                %s
                SELECT COUNT(*) AS count
                FROM unified_sales us
                %s
                """.formatted(UNIFIED_SALES_CTE, whereClause),
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortBy = SORTABLE.contains(filter.sortBy()) ? filter.sortBy() : "sale_date";
        String sortExpr = "customer_name".equals(sortBy) ? "us.customer_name" : "us." + sortBy;
        String sortOrder = "ASC".equalsIgnoreCase(filter.sortOrder()) ? "ASC" : "DESC";

        int page = Math.max(1, filter.currentPage());
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        params.add(limit);
        params.add(offset);

        List<Sale> sales = queryList(
                """
                %s
                SELECT
                  us.*
                FROM unified_sales us
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(UNIFIED_SALES_CTE, whereClause, sortExpr, sortOrder),
                saleMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(sales, total, totalPages, page, limit);
    }

    @Override
    public com.possum.application.sales.dto.SaleStats getSaleStats(SaleFilter filter) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildUnifiedWhere(filter, params);

        return queryOne(
                """
                %s
                SELECT
                    COUNT(*) AS total_bills,
                    SUM(CASE WHEN status IN ('paid', 'legacy') THEN 1 ELSE 0 END) AS paid_count,
                    SUM(CASE WHEN status IN ('partially_paid', 'draft') THEN 1 ELSE 0 END) AS partial_count,
                    SUM(CASE WHEN status IN ('cancelled', 'refunded', 'partially_refunded') THEN 1 ELSE 0 END) AS cancelled_count
                FROM unified_sales us
                %s
                """.formatted(UNIFIED_SALES_CTE, whereClause),
                rs -> new com.possum.application.sales.dto.SaleStats(
                        rs.getLong("total_bills"),
                        rs.getLong("paid_count"),
                        rs.getLong("partial_count"),
                        rs.getLong("cancelled_count")
                ),
                params.toArray()
        ).orElse(new com.possum.application.sales.dto.SaleStats(0, 0, 0, 0));
    }

    @Override
    public int updateSaleStatus(long id, String status) {
        return executeUpdate("UPDATE sales SET status = ? WHERE id = ?", status, id);
    }

    @Override
    public int updateFulfillmentStatus(long id, String status) {
        return executeUpdate("UPDATE sales SET fulfillment_status = ? WHERE id = ?", status, id);
    }

    @Override
    public int updateSalePaidAmount(long id, BigDecimal paidAmount) {
        return executeUpdate("UPDATE sales SET paid_amount = ? WHERE id = ?", paidAmount, id);
    }

    @Override
    public long insertTransaction(Transaction transaction, Long saleId) {
        return executeInsert(
                """
                INSERT INTO transactions (sale_id, amount, type, payment_method_id, status)
                VALUES (?, ?, ?, ?, ?)
                """,
                saleId,
                transaction.amount(),
                transaction.type(),
                transaction.paymentMethodId(),
                transaction.status() == null ? "completed" : transaction.status()
        );
    }

    @Override
    public Optional<String> getLastSaleInvoiceNumber() {
        return queryOne(
                "SELECT invoice_number FROM sales ORDER BY id DESC LIMIT 1",
                rs -> rs.getString("invoice_number")
        );
    }

    @Override
    public List<PaymentMethod> findPaymentMethods() {
        return queryList(
                "SELECT id, name, code, is_active FROM payment_methods WHERE is_active = 1",
                rs -> new PaymentMethod(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("code"),
                        rs.getInt("is_active") == 1
                )
        );
    }

    @Override
    public boolean paymentMethodExists(long id) {
        return queryOne("SELECT id FROM payment_methods WHERE id = ? AND is_active = 1", rs -> rs.getLong("id"), id).isPresent();
    }

    @Override
    public boolean saleExists(long id) {
        return queryOne("SELECT id FROM sales WHERE id = ?", rs -> rs.getLong("id"), id).isPresent();
    }

    @Override
    public Optional<String> getPaymentMethodCode(long paymentMethodId) {
        return queryOne(
                "SELECT code FROM payment_methods WHERE id = ?",
                rs -> rs.getString("code"),
                paymentMethodId
        );
    }

    @Override
    public long getNextSequenceForPaymentType(String paymentTypeCode) {
        // Atomic UPSERT: insert or increment the counter, then return the new value.
        // Uses a direct JDBC call because this must be a two-step operation within
        // the same connection/transaction context.
        Connection conn = connection();
        try {
            try (PreparedStatement upsert = conn.prepareStatement(
                    """
                    INSERT INTO invoice_sequences (payment_type_code, last_sequence)
                    VALUES (?, 1)
                    ON CONFLICT(payment_type_code) DO UPDATE SET last_sequence = last_sequence + 1
                    """)) {
                upsert.setString(1, paymentTypeCode);
                upsert.executeUpdate();
            }
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT last_sequence FROM invoice_sequences WHERE payment_type_code = ?")) {
                select.setString(1, paymentTypeCode);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("last_sequence");
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get next sequence for payment type: " + paymentTypeCode, e);
        }
        throw new IllegalStateException("No sequence row found for payment type: " + paymentTypeCode);
    }

    private static String buildUnifiedWhere(SaleFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        if (filter.status() != null && !filter.status().isEmpty()) {
            joiner.add("us.status IN (" + "?,".repeat(filter.status().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.status());
        }
        if (filter.fulfillmentStatus() != null && !filter.fulfillmentStatus().isEmpty()) {
            joiner.add("us.fulfillment_status IN (" + "?,".repeat(filter.fulfillmentStatus().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.fulfillmentStatus());
        }
        if (filter.customerId() != null) {
            joiner.add("us.customer_id = ?");
            params.add(filter.customerId());
        }
        if (filter.userId() != null) {
            joiner.add("us.user_id = ?");
            params.add(filter.userId());
        }
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            String date = filter.startDate().substring(0, Math.min(10, filter.startDate().length()));
            joiner.add("us.sale_date >= ?");
            params.add(date + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            String date = filter.endDate().substring(0, Math.min(10, filter.endDate().length()));
            joiner.add("us.sale_date <= ?");
            params.add(date + " 23:59:59");
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            String fuzzy = "%" + filter.searchTerm() + "%";
            joiner.add("(us.invoice_number LIKE ? OR COALESCE(us.customer_name, '') LIKE ?)");
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (filter.minAmount() != null) {
            joiner.add("us.total_amount >= ?");
            params.add(filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            joiner.add("us.total_amount <= ?");
            params.add(filter.maxAmount());
        }
        if (filter.paymentMethodIds() != null && !filter.paymentMethodIds().isEmpty()) {
            joiner.add("us.payment_method_id IN (" + "?,".repeat(filter.paymentMethodIds().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.paymentMethodIds());
        }
        if (joiner.length() == 0) {
            return "";
        }
        return "WHERE " + joiner;
    }

    @Override
    public int updateTransactionPaymentMethod(long saleId, long newPaymentMethodId) {
        return executeUpdate(
                "UPDATE transactions SET payment_method_id = ? WHERE sale_id = ? AND type = 'payment'",
                newPaymentMethodId,
                saleId
        );
    }

    @Override
    public int updateSaleCustomer(long saleId, Long customerId) {
        return executeUpdate("UPDATE sales SET customer_id = ? WHERE id = ?", customerId, saleId);
    }

    @Override
    public int deleteSaleItem(long itemId) {
        return executeUpdate("DELETE FROM sale_items WHERE id = ?", itemId);
    }

    @Override
    public int updateSaleItem(SaleItem item) {
        return executeUpdate(
                """
                UPDATE sale_items SET 
                  quantity = ?, price_per_unit = ?, cost_per_unit = ?, 
                  tax_rate = ?, tax_amount = ?, discount_amount = ?,
                  applied_tax_rate = ?, applied_tax_amount = ?, tax_rule_snapshot = ?
                WHERE id = ?
                """,
                item.quantity(),
                item.pricePerUnit(),
                item.costPerUnit(),
                item.taxRate(),
                item.taxAmount(),
                item.discountAmount(),
                item.appliedTaxRate(),
                item.appliedTaxAmount(),
                item.taxRuleSnapshot(),
                item.id()
        );
    }

    @Override
    public int updateSaleTotals(long saleId, BigDecimal totalAmount, BigDecimal totalTax, BigDecimal discount) {
        return executeUpdate(
                "UPDATE sales SET total_amount = ?, total_tax = ?, discount = ? WHERE id = ?",
                totalAmount,
                totalTax,
                discount,
                saleId
        );
    }

    @Override
    public boolean upsertLegacySale(LegacySale legacySale) {
        String saleDate = legacySale.saleDate() != null
                ? legacySale.saleDate().toString().replace('T', ' ')
                : null;

        return executeUpdate(
                """
                INSERT INTO legacy_sales (
                    invoice_number, sale_date, customer_code, customer_name, net_amount,
                    payment_method_id, payment_method_name, source_file, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(invoice_number) DO UPDATE SET
                    sale_date = excluded.sale_date,
                    customer_code = excluded.customer_code,
                    customer_name = excluded.customer_name,
                    net_amount = excluded.net_amount,
                    payment_method_id = excluded.payment_method_id,
                    payment_method_name = excluded.payment_method_name,
                    source_file = excluded.source_file,
                    updated_at = CURRENT_TIMESTAMP
                """,
                legacySale.invoiceNumber(),
                saleDate,
                legacySale.customerCode(),
                legacySale.customerName(),
                legacySale.netAmount(),
                legacySale.paymentMethodId(),
                legacySale.paymentMethodName(),
                legacySale.sourceFile()
        ) > 0;
    }

}

package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.LegacySale;
import com.picopossum.domain.model.PaymentMethod;
import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.SaleItemMapper;
import com.picopossum.persistence.mappers.SaleMapper;

import com.picopossum.domain.repositories.SalesRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.SaleFilter;

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
                s.status AS status,
                s.fulfillment_status AS fulfillment_status,
                s.customer_id AS customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                c.email AS customer_email,
                'System Admin' AS biller_name,
                s.payment_method_id,
                pm.name AS payment_method_name,
                s.invoice_id
              FROM sales s
              LEFT JOIN customers c ON s.customer_id = c.id
              LEFT JOIN payment_methods pm ON s.payment_method_id = pm.id

              UNION ALL

              SELECT
                -ls.id AS id,
                ls.invoice_number AS invoice_number,
                ls.sale_date AS sale_date,
                ls.net_amount AS total_amount,
                ls.net_amount AS paid_amount,
                0 AS discount,
                'legacy' AS status,
                'fulfilled' AS fulfillment_status,
                NULL AS customer_id,
                CASE WHEN ls.customer_name IS NULL OR trim(ls.customer_name) = '' THEN 'Walk-in Customer' ELSE ls.customer_name END AS customer_name,
                NULL AS customer_phone,
                NULL AS customer_email,
                'Legacy Import' AS biller_name,
                ls.payment_method_id AS payment_method_id,
                COALESCE(NULLIF(trim(ls.payment_method_name), ''), 'Legacy Import') AS payment_method_name,
                ls.invoice_number AS invoice_id
              FROM legacy_sales ls
            )
            """;

    private final SaleMapper saleMapper = new SaleMapper();
    private final SaleItemMapper saleItemMapper = new SaleItemMapper();


    public SqliteSalesRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    public SqliteSalesRepository(ConnectionProvider connectionProvider, com.picopossum.infrastructure.monitoring.PerformanceMonitor performanceMonitor) {
        super(connectionProvider, performanceMonitor);
    }

    @Override
    public long insertSale(Sale sale) {
        return executeInsert(
                """
                INSERT INTO sales (
                  invoice_number, invoice_id, total_amount, paid_amount, discount, status, fulfillment_status, customer_id, payment_method_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sale.invoiceNumber(),
                sale.invoiceId(),
                sale.totalAmount(),
                sale.paidAmount(),
                sale.discount(),
                sale.status(),
                sale.fulfillmentStatus() == null ? "pending" : sale.fulfillmentStatus(),
                sale.customerId(),
                sale.paymentMethodId()
        );
    }

    @Override
    public long insertSaleItem(SaleItem item) {
        return executeInsert(
                """
                INSERT INTO sale_items (
                  sale_id, product_id, quantity, price_per_unit, cost_per_unit, discount_amount
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                item.saleId(),
                item.productId(),
                item.quantity(),
                item.pricePerUnit(),
                item.costPerUnit(),
                item.discountAmount()
        );
    }

    @Override
    public Optional<Sale> findSaleById(long id) {
        return queryOne(
                """
                SELECT
                  s.*, c.name AS customer_name, c.phone AS customer_phone, c.email AS customer_email, 'System Admin' AS biller_name,
                  pm.name AS payment_method_name
                FROM sales s
                LEFT JOIN customers c ON s.customer_id = c.id
                LEFT JOIN payment_methods pm ON s.payment_method_id = pm.id
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
                  s.*, c.name AS customer_name, c.phone AS customer_phone, c.email AS customer_email, 'System Admin' AS biller_name,
                  pm.name AS payment_method_name
                FROM sales s
                LEFT JOIN customers c ON s.customer_id = c.id
                LEFT JOIN payment_methods pm ON s.payment_method_id = pm.id
                WHERE s.invoice_number = ?
                GROUP BY s.id
                """;
        
        Optional<Sale> result = queryOne(query, saleMapper, invoiceNumber);
        if (result.isPresent()) return result;

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
                  si.*, p.sku, p.name AS product_name,
                  (SELECT COALESCE(SUM(ri.quantity), 0) FROM return_items ri WHERE ri.sale_item_id = si.id) AS returned_quantity
                FROM sale_items si
                JOIN products p ON si.product_id = p.id
                WHERE si.sale_id = ?
                """,
                saleItemMapper,
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
    public com.picopossum.application.sales.dto.SaleStats getSaleStats(SaleFilter filter) {
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
                rs -> new com.picopossum.application.sales.dto.SaleStats(
                        rs.getLong("total_bills"),
                        rs.getLong("paid_count"),
                        rs.getLong("partial_count"),
                        rs.getLong("cancelled_count")
                ),
                params.toArray()
        ).orElse(new com.picopossum.application.sales.dto.SaleStats(0, 0, 0, 0));
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
        WhereBuilder where = new WhereBuilder();
        if (filter.status() != null && !filter.status().isEmpty()) {
            where.addIn("us.status", filter.status());
        }
        if (filter.fulfillmentStatus() != null && !filter.fulfillmentStatus().isEmpty()) {
            where.addIn("us.fulfillment_status", filter.fulfillmentStatus());
        }
        if (filter.customerId() != null) {
            where.addCondition("us.customer_id = ?", filter.customerId());
        }
        if (filter.startDate() != null && !filter.startDate().isBlank()) {
            String date = filter.startDate().substring(0, Math.min(10, filter.startDate().length()));
            where.addCondition("us.sale_date >= ?", date + " 00:00:00");
        }
        if (filter.endDate() != null && !filter.endDate().isBlank()) {
            String date = filter.endDate().substring(0, Math.min(10, filter.endDate().length()));
            where.addCondition("us.sale_date <= ?", date + " 23:59:59");
        }
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            String fuzzy = "%" + filter.searchTerm() + "%";
            where.addCondition("(us.invoice_number LIKE ? OR COALESCE(us.customer_name, '') LIKE ?)", fuzzy, fuzzy);
        }
        if (filter.minAmount() != null) {
            where.addCondition("us.total_amount >= ?", filter.minAmount());
        }
        if (filter.maxAmount() != null) {
            where.addCondition("us.total_amount <= ?", filter.maxAmount());
        }
        if (filter.paymentMethodIds() != null && !filter.paymentMethodIds().isEmpty()) {
            where.addIn("us.payment_method_id", filter.paymentMethodIds());
        }
        params.addAll(where.getParams());
        return where.build();
    }

    @Override
    public int updateSalePaymentMethod(long saleId, long paymentMethodId) {
        return executeUpdate("UPDATE sales SET payment_method_id = ? WHERE id = ?", paymentMethodId, saleId);
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
                  discount_amount = ?
                WHERE id = ?
                """,
                item.quantity(),
                item.pricePerUnit(),
                item.costPerUnit(),
                item.discountAmount(),
                item.id()
        );
    }

    @Override
    public int updateSaleTotals(long saleId, BigDecimal totalAmount, BigDecimal discount) {
        return executeUpdate(
                "UPDATE sales SET total_amount = ?, discount = ? WHERE id = ?",
                totalAmount,
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

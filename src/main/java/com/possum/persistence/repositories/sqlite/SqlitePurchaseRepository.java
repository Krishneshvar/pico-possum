package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.PurchaseOrder;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.PurchaseOrderItemMapper;
import com.possum.persistence.mappers.PurchaseOrderMapper;
import com.possum.domain.repositories.PurchaseRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqlitePurchaseRepository extends BaseSqliteRepository implements PurchaseRepository {

    private final PurchaseOrderMapper purchaseOrderMapper = new PurchaseOrderMapper();
    private final PurchaseOrderItemMapper itemMapper = new PurchaseOrderItemMapper();

    public SqlitePurchaseRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public PagedResult<PurchaseOrder> getAllPurchaseOrders(PurchaseOrderFilter filter) {
        int page = Math.max(1, filter.page());
        int limit = Math.max(1, filter.limit());
        int offset = (page - 1) * limit;
        List<Object> params = new ArrayList<>();
        String where = buildWhere(filter, params);

        int total = queryOne(
                """
                SELECT COUNT(*) AS count
                FROM purchase_orders po
                LEFT JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN payment_methods pm ON po.payment_method_id = pm.id
                %s
                """.formatted(where),
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortBy = switch (filter.sortBy() == null ? "order_date" : filter.sortBy()) {
            case "id" -> "po.id";
            case "supplier_name" -> "supplier_name";
            case "status" -> "po.status";
            case "item_count" -> "item_count";
            case "total_cost" -> "total_cost";
            default -> "po.order_date";
        };
        String sortOrder = "ASC".equalsIgnoreCase(filter.sortOrder()) ? "ASC" : "DESC";

        params.add(limit);
        params.add(offset);
        List<PurchaseOrder> orders = queryList(
                """
                SELECT
                  po.*,
                  s.name AS supplier_name,
                  pm.name AS payment_method_name,
                  u.name AS created_by_name,
                  (SELECT COUNT(*) FROM purchase_order_items WHERE purchase_order_id = po.id) AS item_count,
                  (SELECT SUM(quantity * unit_cost) FROM purchase_order_items WHERE purchase_order_id = po.id) AS total_cost
                FROM purchase_orders po
                LEFT JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN payment_methods pm ON po.payment_method_id = pm.id
                LEFT JOIN users u ON po.created_by = u.id
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(where, sortBy, sortOrder),
                purchaseOrderMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(orders, total, totalPages, page, limit);
    }

    @Override
    public Optional<PurchaseOrder> getPurchaseOrderById(long id) {
        return queryOne(
                """
                SELECT
                  po.*, s.name AS supplier_name, pm.name AS payment_method_name, u.name AS created_by_name,
                  (SELECT COUNT(*) FROM purchase_order_items WHERE purchase_order_id = po.id) AS item_count,
                  (SELECT SUM(quantity * unit_cost) FROM purchase_order_items WHERE purchase_order_id = po.id) AS total_cost
                FROM purchase_orders po
                LEFT JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN payment_methods pm ON po.payment_method_id = pm.id
                LEFT JOIN users u ON po.created_by = u.id
                WHERE po.id = ?
                """,
                purchaseOrderMapper,
                id
        );
    }

    @Override
    public List<PurchaseOrderItem> getPurchaseOrderItems(long purchaseOrderId) {
        return queryList(
                """
                SELECT
                  poi.*, v.name AS variant_name, v.sku, p.name AS product_name
                FROM purchase_order_items poi
                JOIN variants v ON poi.variant_id = v.id
                JOIN products p ON v.product_id = p.id
                WHERE poi.purchase_order_id = ?
                """,
                itemMapper,
                purchaseOrderId
        );
    }

    @Override
    public long createPurchaseOrder(long supplierId, String invoiceNumber, long paymentMethodId, long createdBy, List<PurchaseOrderItem> items) {
        boolean supplierExists = queryOne("SELECT id FROM suppliers WHERE id = ? AND deleted_at IS NULL", rs -> rs.getLong("id"), supplierId).isPresent();
        if (!supplierExists) {
            throw new IllegalStateException("Supplier not found: " + supplierId);
        }
        long poId = executeInsert(
                "INSERT INTO purchase_orders (supplier_id, invoice_number, payment_method_id, status, created_by) VALUES (?, ?, ?, 'pending', ?)",
                supplierId,
                invoiceNumber,
                paymentMethodId,
                createdBy
        );
        for (PurchaseOrderItem item : items) {
            boolean variantExists = queryOne("SELECT id FROM variants WHERE id = ?", rs -> rs.getLong("id"), item.variantId()).isPresent();
            if (!variantExists) {
                throw new IllegalStateException("Variant not found: " + item.variantId());
            }
            executeInsert(
                    "INSERT INTO purchase_order_items (purchase_order_id, variant_id, quantity, unit_cost) VALUES (?, ?, ?, ?)",
                    poId,
                    item.variantId(),
                    item.quantity(),
                    item.unitCost()
            );
        }
        return poId;
    }

    @Override
    public boolean updatePurchaseOrder(long id, long supplierId, long paymentMethodId, List<PurchaseOrderItem> items) {
        boolean pending = queryOne(
                "SELECT id FROM purchase_orders WHERE id = ? AND status = 'pending'",
                rs -> rs.getLong("id"),
                id
        ).isPresent();
        if (!pending) {
            throw new IllegalStateException("Purchase order not pending: " + id);
        }
        executeUpdate("UPDATE purchase_orders SET supplier_id = ?, payment_method_id = ? WHERE id = ?", supplierId, paymentMethodId, id);
        executeUpdate("DELETE FROM purchase_order_items WHERE purchase_order_id = ?", id);
        for (PurchaseOrderItem item : items) {
            executeInsert(
                    "INSERT INTO purchase_order_items (purchase_order_id, variant_id, quantity, unit_cost) VALUES (?, ?, ?, ?)",
                    id,
                    item.variantId(),
                    item.quantity(),
                    item.unitCost()
            );
        }
        return true;
    }

    @Override
    public boolean receivePurchaseOrder(long purchaseOrderId, long userId) {
        int rowsAffected = executeUpdate(
                "UPDATE purchase_orders SET status = 'received', received_date = CURRENT_TIMESTAMP WHERE id = ? AND status = 'pending'",
                purchaseOrderId
        );
        return rowsAffected > 0;
    }

    @Override
    public int cancelPurchaseOrder(long id) {
        return executeUpdate("UPDATE purchase_orders SET status = 'cancelled' WHERE id = ? AND status = 'pending'", id);
    }

    private static String buildWhere(PurchaseOrderFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        joiner.add("1=1");
        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            joiner.add("(s.name LIKE ? OR po.id LIKE ? OR po.invoice_number LIKE ? OR pm.name LIKE ?)");
            String fuzzy = "%" + filter.searchTerm() + "%";
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            joiner.add("po.status IN (" + "?,".repeat(filter.statuses().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.statuses());
        }
        if (filter.paymentMethodIds() != null && !filter.paymentMethodIds().isEmpty()) {
            joiner.add("po.payment_method_id IN (" + "?,".repeat(filter.paymentMethodIds().size()).replaceAll(",$", "") + ")");
            params.addAll(filter.paymentMethodIds());
        }
        if (filter.fromDate() != null && !filter.fromDate().isBlank()) {
            joiner.add("po.order_date >= ?");
            params.add(filter.fromDate());
        }
        if (filter.toDate() != null && !filter.toDate().isBlank()) {
            joiner.add("po.order_date <= ?");
            params.add(filter.toDate());
        }
        
        if (filter.minPrice() != null) {
            joiner.add("(SELECT COALESCE(SUM(quantity * unit_cost), 0) FROM purchase_order_items WHERE purchase_order_id = po.id) >= ?");
            params.add(filter.minPrice().doubleValue());
        }
        if (filter.maxPrice() != null) {
            joiner.add("(SELECT COALESCE(SUM(quantity * unit_cost), 0) FROM purchase_order_items WHERE purchase_order_id = po.id) <= ?");
            params.add(filter.maxPrice().doubleValue());
        }
        return "WHERE " + joiner.toString();
    }
}

package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Product;
import com.picopossum.domain.model.StockMovement;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.ProductMapper;
import com.picopossum.persistence.mappers.StockHistoryMapper;
import com.picopossum.persistence.mappers.StockMovementMapper;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.shared.dto.StockHistoryDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimalist implementation of InventoryRepository.
 * Optimized for SMB single-user performance (O(1) stock lookups).
 */
public final class SqliteInventoryRepository extends BaseSqliteRepository implements InventoryRepository {

    private final StockMovementMapper movementMapper = new StockMovementMapper();
    private final StockHistoryMapper historyMapper = new StockHistoryMapper();
    private final ProductMapper productMapper = new ProductMapper();

    public SqliteInventoryRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    public SqliteInventoryRepository(ConnectionProvider connectionProvider, com.picopossum.infrastructure.monitoring.PerformanceMonitor performanceMonitor) {
        super(connectionProvider, performanceMonitor);
    }

    @Override
    public int getStockByProductId(long productId) {
        return queryOne(
                "SELECT current_stock FROM product_stock_cache WHERE product_id = ?",
                rs -> rs.getInt("current_stock"),
                productId
        ).orElse(0);
    }

    @Override
    public List<StockMovement> findMovementsByProductId(long productId, int limit, int offset) {
        return queryList(
                """
                SELECT * FROM stock_movements 
                WHERE product_id = ? 
                ORDER BY created_at DESC 
                LIMIT ? OFFSET ?
                """,
                movementMapper,
                productId,
                limit,
                offset
        );
    }

    @Override
    public List<StockHistoryDto> findStockHistory(String search, List<String> reasons, String fromDate, String toDate, int limit, int offset) {
        WhereBuilder where = new WhereBuilder();
        where.addCondition("1=1");

        if (search != null && !search.trim().isEmpty()) {
            where.addCondition("(p.name LIKE ? OR p.sku LIKE ?)", "%" + search.trim() + "%", "%" + search.trim() + "%");
        }

        if (reasons != null && !reasons.isEmpty()) {
            where.addIn("sm.reason", reasons);
        }

        if (fromDate != null && !fromDate.trim().isEmpty()) {
            where.addCondition("sm.created_at >= ?", fromDate);
        }

        if (toDate != null && !toDate.trim().isEmpty()) {
            where.addCondition("sm.created_at <= ?", toDate);
        }

        String baseSql = """
                SELECT
                    sm.id,
                    sm.product_id,
                    p.name AS product_name,
                    p.sku,
                    sm.quantity_change,
                    sm.reason,
                    sm.created_at,
                    p.stock_alert_cap,
                    COALESCE(sc.current_stock, 0) AS current_stock
                FROM stock_movements sm
                JOIN products p ON sm.product_id = p.id
                LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
                """;

        String finalSql = baseSql + " " + where.build() + " ORDER BY sm.created_at DESC LIMIT ? OFFSET ?";
        
        List<Object> allParams = new ArrayList<>(where.getParams());
        allParams.add(limit);
        allParams.add(offset);

        return queryList(finalSql, historyMapper, allParams.toArray());
    }

    @Override
    public long insertStockMovement(StockMovement movement) {
        return executeInsert(
                """
                INSERT INTO stock_movements (
                    product_id, quantity_change, reason, reference_type, reference_id, notes
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                movement.productId(),
                movement.quantityChange(),
                movement.reason(),
                movement.referenceType(),
                movement.referenceId(),
                movement.notes()
        );
    }

    @Override
    public List<Product> findLowStockProducts() {
        return queryList(
                """
                SELECT
                  p.id, p.name, p.description, p.category_id, c.name AS category_name,
                  p.tax_rate, p.sku, p.barcode, p.mrp, p.cost_price, p.stock_alert_cap,
                  p.status, p.image_path, 
                  COALESCE(sc.current_stock, 0) AS stock, 
                  p.created_at, p.updated_at, p.deleted_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
                WHERE p.deleted_at IS NULL
                  AND COALESCE(sc.current_stock, 0) <= p.stock_alert_cap
                ORDER BY stock ASC
                """,
                productMapper
        );
    }

    @Override
    public Map<String, Object> getInventoryStats() {
        return queryOne(
                """
                SELECT
                  COALESCE(SUM(current_stock), 0) AS totalItemsInStock,
                  COUNT(CASE WHEN current_stock = 0 THEN 1 END) AS productsWithNoStock,
                  COUNT(CASE WHEN current_stock <= p.stock_alert_cap THEN 1 END) AS productsWithLowStock
                FROM products p
                LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
                WHERE p.deleted_at IS NULL
                """,
                rs -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("totalItemsInStock", rs.getInt("totalItemsInStock"));
                    map.put("productsWithNoStock", rs.getInt("productsWithNoStock"));
                    map.put("productsWithLowStock", rs.getInt("productsWithLowStock"));
                    return map;
                }
        ).orElse(Map.of("totalItemsInStock", 0, "productsWithNoStock", 0, "productsWithLowStock", 0));
    }

}

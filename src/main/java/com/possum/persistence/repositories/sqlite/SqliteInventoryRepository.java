package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Product;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.InventoryAdjustmentMapper;
import com.possum.persistence.mappers.InventoryLotMapper;
import com.possum.persistence.mappers.ProductMapper;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.shared.dto.AvailableLot;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteInventoryRepository extends BaseSqliteRepository implements InventoryRepository {

    private static final String STOCK_SQL = """
            COALESCE((SELECT SUM(quantity) FROM inventory_lots WHERE product_id = p.id), 0)
            + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE product_id = p.id AND (reason != 'confirm_receive' OR lot_id IS NULL)), 0)
            """;

    private final InventoryLotMapper lotMapper = new InventoryLotMapper();
    private final InventoryAdjustmentMapper adjustmentMapper = new InventoryAdjustmentMapper();
    private final ProductMapper productMapper = new ProductMapper();

    public SqliteInventoryRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public int getStockByProductId(long productId) {
        return queryOne(
                """
                SELECT
                  COALESCE((SELECT SUM(quantity) FROM inventory_lots WHERE product_id = ?), 0)
                  + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE product_id = ? AND (reason != 'confirm_receive' OR lot_id IS NULL)), 0) AS stock
                """,
                rs -> rs.getInt("stock"),
                productId,
                productId
        ).orElse(0);
    }

    @Override
    public List<InventoryLot> findLotsByProductId(long productId) {
        return queryList(
                """
                SELECT il.*
                FROM inventory_lots il
                WHERE il.product_id = ?
                ORDER BY il.created_at DESC
                """,
                lotMapper,
                productId
        );
    }

    @Override
    public List<AvailableLot> findAvailableLotsByProductId(long productId) {
        return queryList(
                """
                SELECT 
                  il.id, il.product_id, il.batch_number, il.manufactured_date, il.expiry_date,
                  il.quantity AS initial_quantity, il.unit_cost, il.created_at,
                  (il.quantity + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE lot_id = il.id), 0)) AS remaining_quantity
                FROM inventory_lots il
                WHERE il.product_id = ?
                  AND (il.quantity + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE lot_id = il.id), 0)) > 0
                ORDER BY il.created_at ASC
                """,
                rs -> new AvailableLot(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("batch_number"),
                        SqlMapperUtils.getLocalDateTime(rs, "manufactured_date"),
                        SqlMapperUtils.getLocalDateTime(rs, "expiry_date"),
                        rs.getInt("initial_quantity"),
                        SqlMapperUtils.getBigDecimal(rs, "unit_cost"),
                        SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                        rs.getInt("remaining_quantity")
                ),
                productId
        );
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public List<com.possum.shared.dto.StockHistoryDto> findStockHistory(String search, List<String> reasons, String fromDate, String toDate, List<Long> userIds, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    ia.id,
                    ia.product_id,
                    p.name AS product_name,
                    p.sku,
                    ia.quantity_change,
                    ia.reason,
                    u.name AS adjusted_by_name,
                    ia.adjusted_at
                FROM inventory_adjustments ia
                JOIN products p ON ia.product_id = p.id
                LEFT JOIN users u ON ia.adjusted_by = u.id
                WHERE 1=1
                """);

        java.util.List<Object> params = new java.util.ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR p.sku LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        if (reasons != null && !reasons.isEmpty()) {
            sql.append(" AND ia.reason IN (");
            sql.append("?,".repeat(reasons.size()));
            sql.setLength(sql.length() - 1); // remove last comma
            sql.append(") ");
            params.addAll(reasons);
        }

        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append(" AND ia.adjusted_at >= ? ");
            params.add(fromDate);
        }

        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append(" AND ia.adjusted_at <= ? ");
            params.add(toDate);
        }

        if (userIds != null && !userIds.isEmpty()) {
            sql.append(" AND ia.adjusted_by IN (");
            sql.append("?,".repeat(userIds.size()));
            sql.setLength(sql.length() - 1);
            sql.append(") ");
            params.addAll(userIds);
        }

        sql.append(" ORDER BY ia.adjusted_at DESC LIMIT ? OFFSET ? ");
        params.add(limit);
        params.add(offset);

        return queryList(sql.toString(), new com.possum.persistence.mappers.StockHistoryMapper(), params.toArray());
    }

    @Override
    public List<InventoryAdjustment> findAdjustmentsByProductId(long productId, int limit, int offset) {
        return queryList(
                """
                SELECT ia.*, u.name AS adjusted_by_name
                FROM inventory_adjustments ia
                LEFT JOIN users u ON ia.adjusted_by = u.id
                WHERE ia.product_id = ?
                ORDER BY ia.adjusted_at DESC
                LIMIT ? OFFSET ?
                """,
                adjustmentMapper,
                productId,
                limit,
                offset
        );
    }

    @Override
    public List<InventoryAdjustment> findAdjustmentsByReference(String referenceType, long referenceId) {
        return queryList(
                "SELECT * FROM inventory_adjustments WHERE reference_type = ? AND reference_id = ?",
                adjustmentMapper,
                referenceType,
                referenceId
        );
    }

    @Override
    public long insertInventoryLot(InventoryLot lot) {
        return executeInsert(
                """
                INSERT INTO inventory_lots (
                  product_id, batch_number, manufactured_date, expiry_date, quantity, unit_cost
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                lot.productId(),
                lot.batchNumber(),
                lot.manufacturedDate(),
                lot.expiryDate(),
                lot.quantity(),
                lot.unitCost()
        );
    }

    @Override
    public long insertInventoryAdjustment(InventoryAdjustment adjustment) {
        return executeInsert(
                """
                INSERT INTO inventory_adjustments (
                  product_id, lot_id, quantity_change, reason, reference_type, reference_id, adjusted_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                adjustment.productId(),
                adjustment.lotId(),
                adjustment.quantityChange(),
                adjustment.reason(),
                adjustment.referenceType(),
                adjustment.referenceId(),
                adjustment.adjustedBy()
        );
    }

    @Override
    public Optional<InventoryLot> findLotById(long id) {
        return queryOne("SELECT * FROM inventory_lots WHERE id = ?", lotMapper, id);
    }

    @Override
    public List<Product> findLowStockProducts() {
        return queryList(
                """
                SELECT
                  p.id, p.name, p.description, p.category_id, c.name AS category_name,
                  p.sku, p.mrp, p.cost_price, p.stock_alert_cap,
                  p.status, p.image_path, (%s) AS stock, p.created_at, p.updated_at, p.deleted_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                WHERE p.deleted_at IS NULL
                  AND (%s) <= p.stock_alert_cap
                ORDER BY stock ASC
                """.formatted(STOCK_SQL, STOCK_SQL),
                productMapper
        );
    }

    @Override
    public List<InventoryLot> findExpiringLots(int days) {
        return queryList(
                """
                SELECT il.*
                FROM inventory_lots il
                JOIN products p ON il.product_id = p.id
                WHERE il.expiry_date IS NOT NULL
                  AND il.expiry_date <= date('now', '+' || ? || ' days')
                  AND il.expiry_date >= date('now')
                  AND p.deleted_at IS NULL
                ORDER BY il.expiry_date ASC
                """,
                lotMapper,
                days
        );
    }

    @Override
    public Map<String, Object> getInventoryStats() {
        return queryOne(
                """
                WITH ProductStock AS (
                  SELECT
                    p.id,
                    p.stock_alert_cap,
                    (%s) AS current_stock
                  FROM products p
                  WHERE p.deleted_at IS NULL
                )
                SELECT
                  COALESCE(SUM(current_stock), 0) AS totalItemsInStock,
                  COUNT(CASE WHEN current_stock = 0 THEN 1 END) AS productsWithNoStock,
                  COUNT(CASE WHEN current_stock <= stock_alert_cap THEN 1 END) AS productsWithLowStock
                FROM ProductStock
                """.formatted(STOCK_SQL),
                rs -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("totalItemsInStock", rs.getInt("totalItemsInStock"));
                    map.put("productsWithNoStock", rs.getInt("productsWithNoStock"));
                    map.put("productsWithLowStock", rs.getInt("productsWithLowStock"));
                    return map;
                }
        ).orElse(Map.<String, Object>of("totalItemsInStock", 0, "productsWithNoStock", 0, "productsWithLowStock", 0));
    }
}

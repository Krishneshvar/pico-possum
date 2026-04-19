package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.ProductFlow;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.domain.repositories.ProductFlowRepository;
import com.picopossum.shared.util.SqlMapperUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SqliteProductFlowRepository extends BaseSqliteRepository implements ProductFlowRepository {

    public SqliteProductFlowRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertProductFlow(ProductFlow flow) {
        return executeInsert(
                "INSERT INTO product_flow (product_id, event_type, quantity, reference_type, reference_id) VALUES (?, ?, ?, ?, ?)",
                flow.productId(),
                flow.eventType(),
                flow.quantity(),
                flow.referenceType(),
                flow.referenceId()
        );
    }

    @Override
    public List<ProductFlow> findFlowByProductId(long productId, int limit, int offset, String startDate, String endDate, List<String> eventTypes) {
        List<Object> params = new ArrayList<>();
        params.add(productId);

        StringBuilder sql = new StringBuilder("""
                SELECT
                  pf.*, p.name AS product_name, 
                  COALESCE(s.id, s_direct.id) AS bill_ref_id,
                  COALESCE(s.invoice_number, s_direct.invoice_number) AS bill_ref_number,
                  c.name AS customer_name,
                  GROUP_CONCAT(DISTINCT pm.name) AS payment_method_names
                FROM product_flow pf
                JOIN products p ON pf.product_id = p.id
                LEFT JOIN sale_items si ON (pf.reference_type = 'sale_item' AND pf.reference_id = si.id)
                LEFT JOIN return_items ri ON (pf.reference_type = 'return_item' AND pf.reference_id = ri.id)
                LEFT JOIN sale_items si_ret ON (ri.sale_item_id = si_ret.id)
                LEFT JOIN sales s ON (s.id = si.sale_id OR s.id = si_ret.sale_id)
                LEFT JOIN sales s_direct ON ( (pf.reference_type IN ('sale_cancellation', 'sale_edit_add', 'sale_edit_reduction') AND pf.reference_id = s_direct.id) )
                LEFT JOIN customers c ON COALESCE(s.customer_id, s_direct.customer_id) = c.id
                LEFT JOIN transactions t ON (COALESCE(s.id, s_direct.id) = t.sale_id AND t.type = 'payment' AND t.status = 'completed')
                LEFT JOIN payment_methods pm ON t.payment_method_id = pm.id
                WHERE pf.product_id = ?
                """);

        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND pf.event_date >= ?");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND pf.event_date <= ?");
            params.add(endDate);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            String placeholders = "?,".repeat(eventTypes.size()).replaceAll(",$", "");
            sql.append(" AND pf.event_type IN (").append(placeholders).append(")");
            params.addAll(eventTypes.stream().map(String::toLowerCase).toList());
        }

        sql.append(" GROUP BY pf.id ORDER BY pf.event_date DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return queryList(
                sql.toString(),
                rs -> new ProductFlow(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("event_type"),
                        rs.getInt("quantity"),
                        rs.getString("reference_type"),
                        rs.getLong("reference_id"),
                        rs.getString("product_name"), rs.getString("customer_name"), rs.getLong("bill_ref_id"), rs.getString("bill_ref_number"),
                        rs.getString("payment_method_names"),
                        SqlMapperUtils.getLocalDateTime(rs, "event_date")
                ),
                params.toArray()
        );
    }

    @Override
    public Map<String, Object> getProductFlowSummary(long productId) {
        return queryOne(
                """
                SELECT
                  SUM(CASE WHEN pf.event_type = 'sale' THEN ABS(pf.quantity) ELSE 0 END) AS total_sold,
                  SUM(CASE WHEN pf.event_type = 'return' THEN pf.quantity ELSE 0 END) AS total_returned,
                  SUM(CASE WHEN pf.event_type = 'adjustment' AND pf.quantity < 0 THEN ABS(pf.quantity) ELSE 0 END) AS total_lost,
                  SUM(CASE WHEN pf.event_type = 'adjustment' AND pf.quantity > 0 THEN pf.quantity ELSE 0 END) AS total_gained,
                  COUNT(pf.id) AS total_events
                FROM product_flow pf
                WHERE pf.product_id = ?
                """,
                rs -> {
                    int sold = rs.getInt("total_sold");
                    int returned = rs.getInt("total_returned");
                    int lost = rs.getInt("total_lost");
                    int gained = rs.getInt("total_gained");
                    int events = rs.getInt("total_events");
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("totalPurchased", 0);
                    map.put("totalSold", sold);
                    map.put("totalReturned", returned);
                    map.put("totalLost", lost);
                    map.put("totalGained", gained);
                    map.put("totalEvents", events);
                    map.put("netMovement", returned + gained - sold - lost);
                    return map;
                },
                productId
        ).orElse(Map.<String, Object>of());
    }

    @Override
    public List<ProductFlow> findFlowByReference(String referenceType, long referenceId) {
        return queryList(
                """
                SELECT pf.*, NULL AS product_name, NULL AS payment_method_names,
                       NULL AS bill_ref_id, NULL AS bill_ref_number, NULL AS customer_name
                FROM product_flow pf
                WHERE reference_type = ? AND reference_id = ?
                ORDER BY event_date DESC
                """,
                rs -> new ProductFlow(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("event_type"),
                        rs.getInt("quantity"),
                        rs.getString("reference_type"),
                        rs.getLong("reference_id"),
                        rs.getString("product_name"), rs.getString("customer_name"),
                        rs.getLong("bill_ref_id"), rs.getString("bill_ref_number"),
                        rs.getString("payment_method_names"),
                        SqlMapperUtils.getLocalDateTime(rs, "event_date")
                ),
                referenceType,
                referenceId
        );
    }
}

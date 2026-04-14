package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Product;
import com.possum.domain.model.TaxRule;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.ProductMapper;
import com.possum.persistence.mappers.TaxRuleMapper;
import com.possum.domain.repositories.ProductRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteProductRepository extends BaseSqliteRepository implements ProductRepository {

    private final ProductMapper productMapper = new ProductMapper();
    private final TaxRuleMapper taxRuleMapper = new TaxRuleMapper();

    public SqliteProductRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertProduct(Product product) {
        return executeInsert(
                """
                INSERT INTO products (name, description, category_id, tax_category_id, sku, mrp, cost_price, stock_alert_cap, status, image_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                product.name(),
                product.description(),
                product.categoryId(),
                product.taxCategoryId(),
                product.sku(),
                product.mrp(),
                product.costPrice(),
                product.stockAlertCap() == null ? 10 : product.stockAlertCap(),
                product.status() == null ? "active" : product.status(),
                product.imagePath()
        );
    }

    @Override
    public Optional<Product> findProductById(long id) {
        return queryOne(
                """
                SELECT
                  p.id, p.name, p.description, p.category_id, c.name AS category_name, p.tax_category_id,
                  tc.name AS tax_category_name, p.sku, p.mrp, p.cost_price, p.stock_alert_cap,
                  p.status, p.image_path,
                  (
                    COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.product_id = p.id), 0)
                    + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.product_id = p.id AND ia.reason != 'confirm_receive'), 0)
                  ) AS stock,
                  p.created_at, p.updated_at, p.deleted_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                WHERE p.id = ? AND p.deleted_at IS NULL
                """,
                productMapper,
                id
        );
    }

    @Override
    public Optional<String> findProductImagePath(long id) {
        return queryOne("SELECT image_path FROM products WHERE id = ?", rs -> rs.getString("image_path"), id);
    }

    @Override
    public int updateProductById(long productId, Product product) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE products SET updated_at = CURRENT_TIMESTAMP");

        if (product.name() != null) {
            sql.append(", name = ?");
            params.add(product.name());
        }
        if (product.description() != null) {
            sql.append(", description = ?");
            params.add(product.description());
        }
        if (product.categoryId() != null) {
            sql.append(", category_id = ?");
            params.add(product.categoryId());
        }
        if (product.taxCategoryId() != null) {
            sql.append(", tax_category_id = ?");
            params.add(product.taxCategoryId());
        }
        if (product.sku() != null) {
            sql.append(", sku = ?");
            params.add(product.sku());
        }
        if (product.mrp() != null) {
            sql.append(", mrp = ?");
            params.add(product.mrp());
        }
        if (product.costPrice() != null) {
            sql.append(", cost_price = ?");
            params.add(product.costPrice());
        }
        if (product.stockAlertCap() != null) {
            sql.append(", stock_alert_cap = ?");
            params.add(product.stockAlertCap());
        }
        if (product.status() != null) {
            sql.append(", status = ?");
            params.add(product.status());
        }
        if (product.imagePath() != null) {
            sql.append(", image_path = ?");
            params.add(product.imagePath());
        }

        if (params.isEmpty()) {
            return 0;
        }

        params.add(productId);
        sql.append(" WHERE id = ?");
        return executeUpdate(sql.toString(), params.toArray());
    }

    @Override
    public int softDeleteProduct(long id) {
        return executeUpdate("UPDATE products SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    @Override
    public PagedResult<Product> findProducts(ProductFilter filter) {
        List<Object> countParams = new ArrayList<>();
        List<Object> queryParams = new ArrayList<>();
        
        String baseSql = """
            SELECT 
                p.id, p.name, p.description, p.category_id, c.name AS category_name, 
                p.tax_category_id, tc.name AS tax_category_name, p.sku, p.mrp, p.cost_price, 
                p.stock_alert_cap, p.status, p.image_path, p.created_at, p.updated_at, p.deleted_at,
                (
                    COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.product_id = p.id), 0)
                    + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.product_id = p.id AND ia.reason != 'confirm_receive'), 0)
                ) AS stock
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
            WHERE p.deleted_at IS NULL
        """;

        StringJoiner filterJoiner = new StringJoiner(" AND ");
        
        if (filter.searchTerm() != null && !filter.searchTerm().trim().isEmpty()) {
            filterJoiner.add("(p.name LIKE ? OR p.sku LIKE ?)");
            String term = "%" + filter.searchTerm().trim() + "%";
            countParams.add(term); countParams.add(term);
            queryParams.add(term); queryParams.add(term);
        }
        
        if (filter.categories() != null && !filter.categories().isEmpty()) {
            filterJoiner.add("p.category_id IN (" + placeholders(filter.categories().size()) + ")");
            countParams.addAll(filter.categories());
            queryParams.addAll(filter.categories());
        }
        
        if (filter.taxCategories() != null && !filter.taxCategories().isEmpty()) {
            filterJoiner.add("p.tax_category_id IN (" + placeholders(filter.taxCategories().size()) + ")");
            countParams.addAll(filter.taxCategories());
            queryParams.addAll(filter.taxCategories());
        }
        
        if (filter.status() != null && !filter.status().isEmpty()) {
            filterJoiner.add("p.status IN (" + placeholders(filter.status().size()) + ")");
            countParams.addAll(filter.status());
            queryParams.addAll(filter.status());
        }

        if (filter.minPrice() != null) {
            filterJoiner.add("p.mrp >= ?");
            countParams.add(filter.minPrice());
            queryParams.add(filter.minPrice());
        }
        
        if (filter.maxPrice() != null) {
            filterJoiner.add("p.mrp <= ?");
            countParams.add(filter.maxPrice());
            queryParams.add(filter.maxPrice());
        }

        String filterStr = filterJoiner.length() > 0 ? " AND " + filterJoiner.toString() : "";
        
        // Stock Status filtering requires wrapping in a subquery or CTE since stock is computed
        String wrappedSql = "SELECT * FROM (" + baseSql + filterStr + ") AS t";
        StringJoiner stockJoiner = new StringJoiner(" OR ");
        
        if (filter.stockStatuses() != null && !filter.stockStatuses().isEmpty()) {
            for (String status : filter.stockStatuses()) {
                switch (status.toLowerCase()) {
                    case "in-stock" -> stockJoiner.add("stock > stock_alert_cap");
                    case "low-stock" -> stockJoiner.add("(stock > 0 AND stock <= stock_alert_cap)");
                    case "out-of-stock" -> stockJoiner.add("stock <= 0");
                }
            }
        }
        
        String stockFilterMatch = stockJoiner.length() > 0 ? " WHERE " + stockJoiner.toString() : "";
        String finalCountSql = "SELECT COUNT(*) FROM (" + wrappedSql + stockFilterMatch + ")";
        
        int total = queryOne(finalCountSql, rs -> rs.getInt(1), countParams.toArray()).orElse(0);
        
        int page = Math.max(1, filter.currentPage() + 1);
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        String sortCol = switch (filter.sortBy() == null ? "name" : filter.sortBy()) {
            case "stock" -> "stock";
            case "price" -> "mrp";
            case "category_name" -> "category_name";
            default -> "name";
        };
        String sortDir = "DESC".equalsIgnoreCase(filter.sortOrder()) ? "DESC" : "ASC";

        String finalQuerySql = wrappedSql + stockFilterMatch + " ORDER BY " + sortCol + " " + sortDir + " LIMIT ? OFFSET ?";
        queryParams.add(limit);
        queryParams.add(offset);
        
        List<Product> items = queryList(finalQuerySql, productMapper, queryParams.toArray());
        int totalPages = (int) Math.ceil((double) total / limit);
        
        return new PagedResult<>(items, total, totalPages, page, limit);
    }

    private String placeholders(int count) {
        return "?,".repeat(count).replaceAll(",$", "");
    }

    @Override
    public List<TaxRule> findProductTaxes(long productId) {
        Optional<Long> taxCategoryId = queryOne(
                "SELECT tax_category_id AS value FROM products WHERE id = ?",
                rs -> {
                    long value = rs.getLong("value");
                    return rs.wasNull() ? null : value;
                },
                productId
        );
        if (taxCategoryId.isEmpty() || taxCategoryId.get() == null) {
            return List.of();
        }
        Optional<Long> activeProfile = queryOne(
                "SELECT id FROM tax_profiles WHERE is_active = 1",
                rs -> rs.getLong("id")
        );
        if (activeProfile.isEmpty()) {
            return List.of();
        }

        return queryList(
                """
                SELECT
                  tr.*, tc.name AS category_name
                FROM tax_rules tr
                INNER JOIN tax_categories tc ON tr.tax_category_id = tc.id
                WHERE tr.tax_profile_id = ?
                  AND tr.tax_category_id = ?
                  AND (tr.valid_from IS NULL OR tr.valid_from <= date('now'))
                  AND (tr.valid_to IS NULL OR tr.valid_to >= date('now'))
                ORDER BY tr.priority DESC
                """,
                taxRuleMapper,
                activeProfile.get(),
                taxCategoryId.get()
        );
    }

    @Override
    public void setProductTaxes(long productId, List<Long> taxIds) { }

    @Override
    public Map<String, Object> getProductStats() {
        return queryOne(
                """
                WITH ProductStats AS (
                  SELECT
                    p.id, p.status, p.stock_alert_cap,
                    (
                      COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.product_id = p.id), 0)
                      + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.product_id = p.id AND ia.reason != 'confirm_receive'), 0)
                    ) AS current_stock
                  FROM products p
                  WHERE p.deleted_at IS NULL
                )
                SELECT
                  COUNT(*) AS totalProducts,
                  COUNT(CASE WHEN status = 'active' THEN 1 END) AS activeProducts,
                  COUNT(CASE WHEN current_stock <= COALESCE(stock_alert_cap, 10) THEN 1 END) AS lowStockProducts
                FROM ProductStats
                """,
                rs -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("totalProducts", rs.getInt("totalProducts"));
                    map.put("activeProducts", rs.getInt("activeProducts"));
                    map.put("lowStockProducts", rs.getInt("lowStockProducts"));
                    return map;
                }
        ).orElse(Map.of("totalProducts", 0, "activeProducts", 0, "lowStockProducts", 0));
    }

    @Override
    public int getNextGeneratedNumericSku() {
        return queryOne("SELECT MAX(CAST(sku AS INTEGER)) AS max_sku FROM products WHERE sku GLOB '[0-9]*'", rs -> rs.getInt("max_sku")).orElse(0) + 1;
    }
}

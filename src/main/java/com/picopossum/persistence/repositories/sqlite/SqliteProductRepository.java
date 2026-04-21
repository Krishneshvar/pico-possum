package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Product;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.ProductMapper;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteProductRepository extends BaseSqliteRepository implements ProductRepository {

    private final ProductMapper productMapper = new ProductMapper();

    public SqliteProductRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertProduct(Product product) {
        return executeInsert(
                """
                INSERT INTO products (name, description, category_id, tax_rate, sku, barcode, mrp, cost_price, stock_alert_cap, status, image_path)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                product.name(),
                product.description(),
                product.categoryId(),
                product.taxRate() == null ? BigDecimal.ZERO : product.taxRate(),
                product.sku(),
                product.barcode(),
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
                  p.id, p.name, p.description, p.category_id, c.name AS category_name,
                  p.tax_rate,
                  p.sku, p.barcode, p.mrp, p.cost_price, p.stock_alert_cap,
                  p.status, p.image_path,
                  COALESCE(sc.current_stock, 0) AS stock,
                  p.created_at, p.updated_at, p.deleted_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
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
        UpdateBuilder builder = new UpdateBuilder("products");
        builder.set("name", product.name())
               .set("description", product.description())
               .set("category_id", product.categoryId())
               .set("tax_rate", product.taxRate())
               .set("sku", product.sku())
               .set("barcode", product.barcode())
               .set("mrp", product.mrp())
               .set("cost_price", product.costPrice())
               .set("stock_alert_cap", product.stockAlertCap())
               .set("status", product.status())
               .set("image_path", product.imagePath())
               .where("id = ?", productId);

        if (!builder.hasFields()) {
            return 0;
        }

        return executeUpdate(builder.getSql(), builder.getParams());
    }

    @Override
    public int softDeleteProduct(long id) {
        return executeUpdate("UPDATE products SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    @Override
    public PagedResult<Product> findProducts(ProductFilter filter) {
        WhereBuilder where = new WhereBuilder()
                .addNotDeleted("p")
                .addSearch(filter.searchTerm(), "p.name", "p.sku", "p.barcode");

        if (filter.categories() != null && !filter.categories().isEmpty()) {
            where.addIn("p.category_id", filter.categories());
        }
        if (filter.status() != null && !filter.status().isEmpty()) {
            where.addIn("p.status", filter.status());
        }
        if (filter.minPrice() != null) {
            where.addCondition("p.mrp >= ?", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            where.addCondition("p.mrp <= ?", filter.maxPrice());
        }

        String baseSql = """
            SELECT 
                p.id, p.name, p.description, p.category_id, c.name AS category_name, 
                p.tax_rate,
                p.sku, p.barcode, p.mrp, p.cost_price, 
                p.stock_alert_cap, p.status, p.image_path, p.created_at, p.updated_at, p.deleted_at,
                COALESCE(sc.current_stock, 0) AS stock
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
            """ + where.build();

        // Wrap for stock filtering
        String wrappedSql = "SELECT * FROM (" + baseSql + ") AS t";
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
        
        String countSql = "SELECT COUNT(*) FROM (" + wrappedSql + stockFilterMatch + ")";
        int total = queryOne(countSql, rs -> rs.getInt(1), where.getParams().toArray()).orElse(0);
        
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
        List<Object> queryParams = new ArrayList<>(where.getParams());
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
    public Map<String, Object> getProductStats() {
        return queryOne(
                """
                SELECT
                  COUNT(*) AS totalProducts,
                  COUNT(CASE WHEN p.status = 'active' THEN 1 END) AS activeProducts,
                  COUNT(CASE WHEN COALESCE(sc.current_stock, 0) <= COALESCE(p.stock_alert_cap, 10) THEN 1 END) AS lowStockProducts
                FROM products p
                LEFT JOIN product_stock_cache sc ON p.id = sc.product_id
                WHERE p.deleted_at IS NULL
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
    public boolean existsBySku(String sku) {
        if (sku == null || sku.isBlank()) return false;
        return queryOne("SELECT 1 FROM products WHERE sku = ? AND deleted_at IS NULL", rs -> true, sku).orElse(false);
    }

    @Override
    public boolean existsBySkuExcludeId(String sku, long id) {
        if (sku == null || sku.isBlank()) return false;
        return queryOne("SELECT 1 FROM products WHERE sku = ? AND id != ? AND deleted_at IS NULL", rs -> true, sku, id).orElse(false);
    }

    @Override
    public int getNextGeneratedNumericSku() {
        return queryOne("SELECT MAX(CAST(sku AS INTEGER)) AS max_sku FROM products WHERE sku GLOB '[0-9]*'", rs -> rs.getInt("max_sku")).orElse(0) + 1;
    }
}

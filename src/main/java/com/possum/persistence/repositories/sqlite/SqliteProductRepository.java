package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Product;
import com.possum.domain.model.TaxRule;
import com.possum.domain.model.Variant;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.ProductMapper;
import com.possum.persistence.mappers.TaxRuleMapper;
import com.possum.persistence.mappers.VariantMapper;
import com.possum.domain.repositories.ProductRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteProductRepository extends BaseSqliteRepository implements ProductRepository {

    private final ProductMapper productMapper = new ProductMapper();
    private final VariantMapper variantMapper = new VariantMapper();
    private final TaxRuleMapper taxRuleMapper = new TaxRuleMapper();
    private final SqliteInventoryRepository inventoryRepository;

    public SqliteProductRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
        this.inventoryRepository = new SqliteInventoryRepository(connectionProvider);
    }

    @Override
    public long insertProduct(Product product) {
        return executeInsert(
                """
                INSERT INTO products (name, description, category_id, tax_category_id, status, image_path)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                product.name(),
                product.description(),
                product.categoryId(),
                product.taxCategoryId(),
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
                  tc.name AS tax_category_name,
                  p.status, p.image_path, p.created_at, p.updated_at, p.deleted_at
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
        List<Object> params = new ArrayList<>();
        String where = buildWhere(filter, params);
        int page = Math.max(1, filter.currentPage() + 1);
        int limit = Math.max(1, filter.itemsPerPage());
        int offset = (page - 1) * limit;

        int total = queryOne(
                """
                SELECT COUNT(DISTINCT p.id) AS count
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                %s
                """.formatted(where),
                rs -> rs.getInt("count"),
                params.toArray()
        ).orElse(0);

        String sortColumn = switch (filter.sortBy() == null ? "name" : filter.sortBy()) {
            case "category_name" -> "c.name";
            case "stock" -> "computed_stock";
            default -> "p.name";
        };
        String sortOrder = "DESC".equalsIgnoreCase(filter.sortOrder()) ? "DESC" : "ASC";

        params.add(limit);
        params.add(offset);
        List<Product> rows = queryList(
                """
                SELECT
                  p.id, p.name, p.description, p.category_id, c.name AS category_name, p.tax_category_id,
                  tc.name AS tax_category_name,
                  p.status, p.image_path,
                  v.id AS variant_id,
                  (
                    COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.variant_id = v.id), 0)
                    + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.variant_id = v.id AND ia.reason != 'confirm_receive'), 0)
                  ) AS computed_stock,
                  (
                    COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.variant_id = v.id), 0)
                    + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.variant_id = v.id AND ia.reason != 'confirm_receive'), 0)
                  ) AS stock,
                  p.created_at, p.updated_at, p.deleted_at
                FROM products p
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                LEFT JOIN variants v ON v.id = (
                  SELECT v2.id
                  FROM variants v2
                  WHERE v2.product_id = p.id AND v2.deleted_at IS NULL
                  ORDER BY v2.is_default DESC, v2.id ASC
                  LIMIT 1
                )
                %s
                GROUP BY p.id
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(where, sortColumn, sortOrder),
                productMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(rows, total, totalPages, page, limit);
    }

    @Override
    public Optional<ProductWithVariants> findProductWithVariants(long productId) {
        Optional<Product> product = findProductById(productId);
        if (product.isEmpty()) {
            return Optional.empty();
        }
        List<Variant> variants = queryList(
                """
                SELECT
                  v.id, v.product_id, p.name AS product_name, v.name, v.sku, v.mrp AS price, v.cost_price, v.stock_alert_cap,
                  v.is_default, v.status, p.image_path, 0 AS stock, c.name AS category_name, tc.name AS tax_category_name,
                  v.created_at, v.updated_at, v.deleted_at
                FROM variants v
                JOIN products p ON v.product_id = p.id
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                WHERE v.product_id = ? AND v.deleted_at IS NULL
                ORDER BY v.is_default DESC, v.name ASC
                """,
                variantMapper,
                productId
        );
        List<Variant> variantsWithStock = new ArrayList<>();
        for (Variant variant : variants) {
            variantsWithStock.add(new Variant(
                    variant.id(),
                    variant.productId(),
                    variant.productName(),
                    variant.name(),
                    variant.sku(),
                    variant.price(),
                    variant.costPrice(),
                    variant.stockAlertCap(),
                    variant.defaultVariant(),
                    variant.status(),
                    variant.imagePath(),
                    inventoryRepository.getStockByVariantId(variant.id()),
                    variant.categoryName(),
                    variant.taxCategoryName(),
                    variant.createdAt(),
                    variant.updatedAt(),
                    variant.deletedAt()
            ));
        }
        return Optional.of(new ProductWithVariants(product.get(), variantsWithStock));
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
    public void setProductTaxes(long productId, List<Long> taxIds) {
        // Current Electron implementation intentionally no-ops this method.
    }

    @Override
    public Map<String, Object> getProductStats() {
        return queryOne(
                """
                WITH ProductStats AS (
                  SELECT
                    p.id,
                    p.status,
                    v.stock_alert_cap,
                    (
                      COALESCE((SELECT SUM(il.quantity) FROM inventory_lots il WHERE il.variant_id = v.id), 0)
                      + COALESCE((SELECT SUM(ia.quantity_change) FROM inventory_adjustments ia WHERE ia.variant_id = v.id AND ia.reason != 'confirm_receive'), 0)
                    ) AS current_stock
                  FROM products p
                  LEFT JOIN variants v ON v.id = (
                    SELECT v2.id
                    FROM variants v2
                    WHERE v2.product_id = p.id AND v2.deleted_at IS NULL
                    ORDER BY v2.is_default DESC, v2.id ASC
                    LIMIT 1
                  )
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
        ).orElse(Map.<String, Object>of("totalProducts", 0, "activeProducts", 0, "lowStockProducts", 0));
    }

    private static String buildWhere(ProductFilter filter, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        joiner.add("p.deleted_at IS NULL");

        if (filter.searchTerm() != null && !filter.searchTerm().isBlank()) {
            joiner.add("p.name LIKE ?");
            params.add("%" + filter.searchTerm() + "%");
        }
        if (filter.categories() != null && !filter.categories().isEmpty()) {
            String placeholders = "?,".repeat(filter.categories().size()).replaceAll(",$", "");
            joiner.add("p.category_id IN (" + placeholders + ")");
            params.addAll(filter.categories());
        }
        if (filter.status() != null && !filter.status().isEmpty()) {
            String placeholders = "?,".repeat(filter.status().size()).replaceAll(",$", "");
            joiner.add("p.status IN (" + placeholders + ")");
            params.addAll(filter.status());
        }
        if (filter.taxCategories() != null && !filter.taxCategories().isEmpty()) {
            String placeholders = "?,".repeat(filter.taxCategories().size()).replaceAll(",$", "");
            joiner.add("p.tax_category_id IN (" + placeholders + ")");
            params.addAll(filter.taxCategories());
        }
        return "WHERE " + joiner;
    }
}

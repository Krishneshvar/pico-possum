package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Variant;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.VariantMapper;
import com.possum.domain.repositories.VariantRepository;
import com.possum.shared.dto.PagedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteVariantRepository extends BaseSqliteRepository implements VariantRepository {

    private final VariantMapper mapper = new VariantMapper();

    public SqliteVariantRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public long insertVariant(long productId, Variant variant) {
        return executeInsert(
                """
                INSERT INTO variants (product_id, name, sku, mrp, cost_price, stock_alert_cap, is_default, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                productId,
                variant.name(),
                variant.sku(),
                variant.price(),
                variant.costPrice(),
                variant.stockAlertCap() == null ? 10 : variant.stockAlertCap(),
                Boolean.TRUE.equals(variant.defaultVariant()) ? 1 : 0,
                variant.status() == null ? "active" : variant.status()
        );
    }

    @Override
    public Optional<Variant> findVariantByIdSync(long id) {
        return queryOne(
                """
                SELECT
                  v.id, v.product_id, p.name AS product_name, v.name, v.sku, v.mrp AS price, v.cost_price,
                  v.stock_alert_cap, v.is_default, v.status, p.image_path, 0 AS stock, c.name AS category_name,
                  tc.name AS tax_category_name,
                  v.created_at, v.updated_at, v.deleted_at
                FROM variants v
                JOIN products p ON v.product_id = p.id
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                WHERE v.id = ? AND v.deleted_at IS NULL
                """,
                mapper,
                id
        );
    }

    @Override
    public int updateVariantById(Variant variant) {
        return executeUpdate(
                """
                UPDATE variants
                SET name = ?, sku = ?, mrp = ?, cost_price = ?, stock_alert_cap = ?, status = ?, is_default = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """,
                variant.name(),
                variant.sku(),
                variant.price(),
                variant.costPrice(),
                variant.stockAlertCap() == null ? 10 : variant.stockAlertCap(),
                variant.status() == null ? "active" : variant.status(),
                Boolean.TRUE.equals(variant.defaultVariant()) ? 1 : 0,
                variant.id()
        );
    }

    @Override
    public int softDeleteVariant(long id) {
        return executeUpdate("UPDATE variants SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL", id);
    }

    @Override
    public int getNextGeneratedNumericSku() {
        return queryOne(
                """
                SELECT
                    COUNT(*) AS total_variants,
                    COALESCE(
                        MAX(
                            CASE
                                WHEN sku IS NOT NULL
                                     AND TRIM(sku) <> ''
                                     AND TRIM(sku) NOT GLOB '*[^0-9]*'
                                THEN CAST(TRIM(sku) AS INTEGER)
                            END
                        ),
                        0
                    ) AS max_numeric_sku
                FROM variants
                WHERE deleted_at IS NULL
                """,
                rs -> {
                    int totalVariants = rs.getInt("total_variants");
                    int maxNumericSku = rs.getInt("max_numeric_sku");
                    return Math.max(totalVariants + 1, maxNumericSku + 1);
                }
        ).orElse(1);
    }

    @Override
    public PagedResult<Variant> findVariants(String searchTerm,
                                             Long categoryId,
                                             List<Long> categories,
                                             List<Long> taxCategories,
                                             List<String> stockStatus,
                                             List<String> status,
                                             java.math.BigDecimal minPrice,
                                             java.math.BigDecimal maxPrice,
                                             String sortBy,
                                             String sortOrder,
                                             int currentPage,
                                             int itemsPerPage) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(searchTerm, categoryId, categories, taxCategories, status, stockStatus, minPrice, maxPrice, params);

        int total = queryOne(
                """
                SELECT COUNT(*) AS total
                FROM variants v
                JOIN products p ON v.product_id = p.id
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                %s
                """.formatted(where),
                rs -> rs.getInt("total"),
                params.toArray()
        ).orElse(0);

        int page = Math.max(1, currentPage + 1);
        int limit = Math.max(1, itemsPerPage);
        int offset = (page - 1) * limit;

        String sortColumn = switch (sortBy == null ? "product_name" : sortBy) {
            case "name" -> "v.name";
            case "sku" -> "v.sku";
            case "price", "mrp" -> "v.mrp";
            case "cost_price" -> "v.cost_price";
            case "stock" -> "stock";
            case "category_name" -> "c.name";
            case "tax_category_name" -> "tc.name";
            case "product_name" -> "p.name";
            default -> "p.name";
        };
        String order = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";

        params.add(limit);
        params.add(offset);

        List<Variant> variants = queryList(
                """
                SELECT
                  v.id, v.product_id, p.name AS product_name, v.name, v.sku, v.mrp AS price, v.cost_price,
                  v.stock_alert_cap, v.is_default, v.status, p.image_path,
                  (
                    COALESCE((SELECT SUM(quantity) FROM inventory_lots WHERE variant_id = v.id), 0)
                    + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE variant_id = v.id AND (reason != 'confirm_receive' OR lot_id IS NULL)), 0)
                  ) AS stock, c.name AS category_name, tc.name AS tax_category_name,
                  v.created_at, v.updated_at, v.deleted_at
                FROM variants v
                JOIN products p ON v.product_id = p.id
                LEFT JOIN categories c ON p.category_id = c.id
                LEFT JOIN tax_categories tc ON p.tax_category_id = tc.id
                %s
                ORDER BY %s %s
                LIMIT ? OFFSET ?
                """.formatted(where, sortColumn, order),
                mapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) total / limit);
        return new PagedResult<>(variants, total, totalPages, page, limit);
    }

    @Override
    public Map<String, Object> getVariantStats() {
        return queryOne(
                """
                SELECT
                  COUNT(*) AS totalVariants,
                  COUNT(CASE WHEN v.status != 'active' THEN 1 END) AS inactiveVariants
                FROM variants v
                JOIN products p ON v.product_id = p.id
                WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL
                """,
                rs -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("totalVariants", rs.getInt("totalVariants"));
                    map.put("inactiveVariants", rs.getInt("inactiveVariants"));
                    return map;
                }
        ).orElse(Map.<String, Object>of("totalVariants", 0, "inactiveVariants", 0));
    }

    private static String buildWhere(String searchTerm, Long categoryId, List<Long> categories, List<Long> taxCategories, List<String> status, List<String> stockStatus, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, List<Object> params) {
        StringJoiner joiner = new StringJoiner(" AND ");
        joiner.add("v.deleted_at IS NULL");
        joiner.add("p.deleted_at IS NULL");

        if (searchTerm != null && !searchTerm.isBlank()) {
            String fuzzy = "%" + searchTerm + "%";
            joiner.add("(p.name LIKE ? OR v.name LIKE ? OR v.sku LIKE ?)");
            params.add(fuzzy);
            params.add(fuzzy);
            params.add(fuzzy);
        }

        if (categories != null && !categories.isEmpty()) {
            String placeholders = "?,".repeat(categories.size()).replaceAll(",$", "");
            joiner.add("p.category_id IN (" + placeholders + ")");
            params.addAll(categories);
        } else if (categoryId != null) {
            joiner.add("p.category_id = ?");
            params.add(categoryId);
        }

        if (taxCategories != null && !taxCategories.isEmpty()) {
            String placeholders = "?,".repeat(taxCategories.size()).replaceAll(",$", "");
            joiner.add("p.tax_category_id IN (" + placeholders + ")");
            params.addAll(taxCategories);
        }

        if (status != null && !status.isEmpty()) {
            String placeholders = "?,".repeat(status.size()).replaceAll(",$", "");
            joiner.add("v.status IN (" + placeholders + ")");
            params.addAll(status);
        }

        if (stockStatus != null && !stockStatus.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            String stockSub = """
                (
                  COALESCE((SELECT SUM(quantity) FROM inventory_lots WHERE variant_id = v.id), 0)
                  + COALESCE((SELECT SUM(quantity_change) FROM inventory_adjustments WHERE variant_id = v.id AND (reason != 'confirm_receive' OR lot_id IS NULL)), 0)
                )
                """;
            for (String s : stockStatus) {
                switch (s) {
                    case "out-of-stock" -> conditions.add(stockSub + " <= 0");
                    case "low-stock" -> conditions.add(stockSub + " > 0 AND " + stockSub + " <= COALESCE(v.stock_alert_cap, 10)");
                    case "in-stock" -> conditions.add(stockSub + " > COALESCE(v.stock_alert_cap, 10)");
                }
            }
            if (!conditions.isEmpty()) {
                joiner.add("(" + String.join(" OR ", conditions) + ")");
            }
        }

        if (minPrice != null) {
            joiner.add("v.mrp >= ?");
            params.add(minPrice);
        }

        if (maxPrice != null) {
            joiner.add("v.mrp <= ?");
            params.add(maxPrice);
        }

        return "WHERE " + joiner;
    }
}

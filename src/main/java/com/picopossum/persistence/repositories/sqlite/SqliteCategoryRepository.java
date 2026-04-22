package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Category;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.CategoryMapper;
import com.picopossum.domain.repositories.CategoryRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteCategoryRepository extends BaseSqliteRepository implements CategoryRepository {

    private final CategoryMapper mapper = new CategoryMapper();

    public SqliteCategoryRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    public SqliteCategoryRepository(ConnectionProvider connectionProvider, com.picopossum.infrastructure.monitoring.PerformanceMonitor performanceMonitor) {
        super(connectionProvider, performanceMonitor);
    }

    @Override
    public List<Category> findAllCategories() {
        return queryList("SELECT * FROM categories WHERE deleted_at IS NULL ORDER BY name ASC", mapper);
    }

    @Override
    public Optional<Category> findCategoryById(long id) {
        return queryOne("SELECT * FROM categories WHERE id = ? AND deleted_at IS NULL", mapper, id);
    }

    @Override
    public Category insertCategory(String name, Long parentId) {
        long id = executeInsert("INSERT INTO categories (name, parent_id) VALUES (?, ?)", name, parentId);
        return findCategoryById(id).orElseThrow();
    }

    @Override
    public int updateCategoryById(long id, String name, boolean parentIdProvided, Long parentId) {
        UpdateBuilder builder = new UpdateBuilder("categories");
        if (name != null) {
            builder.set("name", name);
        }
        if (parentIdProvided) {
            builder.set("parent_id", parentId);
        }
        
        if (!builder.hasFields()) {
            return 0;
        }
        
        builder.where("id = ? AND deleted_at IS NULL", id);
        return executeUpdate(builder.getSql(), builder.getParams());
    }

    @Override
    public int softDeleteCategory(long id) {
        return executeUpdate("UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    @Override
    public boolean hasLinkedProducts(long categoryId) {
        return queryOne("SELECT 1 FROM products WHERE category_id = ? AND deleted_at IS NULL LIMIT 1", 
                        rs -> true, categoryId).orElse(false);
    }

    @Override
    public boolean hasSubcategories(long categoryId) {
        return queryOne("SELECT 1 FROM categories WHERE parent_id = ? AND deleted_at IS NULL LIMIT 1", 
                        rs -> true, categoryId).orElse(false);
    }
}

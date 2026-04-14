package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Category;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.CategoryMapper;
import com.possum.domain.repositories.CategoryRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteCategoryRepository extends BaseSqliteRepository implements CategoryRepository {

    private final CategoryMapper mapper = new CategoryMapper();

    public SqliteCategoryRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
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
        StringBuilder sql = new StringBuilder("UPDATE categories SET updated_at = CURRENT_TIMESTAMP");
        List<Object> params = new java.util.ArrayList<>();
        if (name != null) {
            sql.append(", name = ?");
            params.add(name);
        }
        if (parentIdProvided) {
            sql.append(", parent_id = ?");
            params.add(parentId);
        }
        if (params.isEmpty()) {
            return 0;
        }
        params.add(id);
        sql.append(" WHERE id = ? AND deleted_at IS NULL");
        return executeUpdate(sql.toString(), params.toArray());
    }

    @Override
    public int softDeleteCategory(long id) {
        return executeUpdate("UPDATE categories SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
}

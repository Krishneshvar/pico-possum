package com.possum.domain.repositories;

import com.possum.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> findAllCategories();

    Optional<Category> findCategoryById(long id);

    Category insertCategory(String name, Long parentId);

    int updateCategoryById(long id, String name, boolean parentIdProvided, Long parentId);

    int softDeleteCategory(long id);
}

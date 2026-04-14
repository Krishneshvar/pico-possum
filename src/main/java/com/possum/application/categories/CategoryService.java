package com.possum.application.categories;

import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Category;
import com.possum.domain.repositories.CategoryRepository;

import java.util.ArrayList;
import java.util.List;

public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryTreeNode> getCategoriesAsTree() {
        List<Category> allCategories = categoryRepository.findAllCategories();
        return buildCategoryTree(allCategories, null);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAllCategories();
    }

    public Category getCategoryById(long id) {
        return categoryRepository.findCategoryById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    public Category createCategory(String name, Long parentId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.CATEGORIES_MANAGE);
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Category name is required");
        }
        return categoryRepository.insertCategory(name, parentId);
    }

    public void updateCategory(long id, String name, Long parentId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.CATEGORIES_MANAGE);
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Category name is required");
        }
        boolean parentIdProvided = parentId != null;
        int changes = categoryRepository.updateCategoryById(id, name, parentIdProvided, parentId);
        if (changes == 0) {
            throw new NotFoundException("Category not found");
        }
    }

    public void deleteCategory(long id) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.CATEGORIES_MANAGE);
        int changes = categoryRepository.softDeleteCategory(id);
        if (changes == 0) {
            throw new NotFoundException("Category not found");
        }
    }

    private List<CategoryTreeNode> buildCategoryTree(List<Category> categories, Long parentId) {
        List<CategoryTreeNode> tree = new ArrayList<>();
        categories.stream()
                .filter(category -> {
                    if (parentId == null) {
                        return category.parentId() == null;
                    }
                    return parentId.equals(category.parentId());
                })
                .forEach(category -> {
                    List<CategoryTreeNode> children = buildCategoryTree(categories, category.id());
                    tree.add(new CategoryTreeNode(category, children));
                });
        return tree;
    }

    public record CategoryTreeNode(Category category, List<CategoryTreeNode> subcategories) {
    }
}

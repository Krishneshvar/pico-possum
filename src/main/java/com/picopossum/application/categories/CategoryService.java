package com.picopossum.application.categories;

import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.Category;
import com.picopossum.domain.repositories.CategoryRepository;

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

    public java.util.Optional<Category> findCategoryById(long id) {
        return categoryRepository.findCategoryById(id);
    }

    public Category createCategory(String name, Long parentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Category name is required");
        }
        return categoryRepository.insertCategory(name, parentId);
    }

    public void updateCategory(long id, String name, Long parentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Category name is required");
        }
        
        if (parentId != null && parentId == id) {
            throw new ValidationException("A category cannot be its own parent");
        }

        // Circular reference check
        if (parentId != null) {
            if (isDescendant(id, parentId)) {
                throw new ValidationException("Circular reference detected: Target parent is a subcategory of this category");
            }
        }

        boolean parentIdProvided = parentId != null;
        int changes = categoryRepository.updateCategoryById(id, name, parentIdProvided, parentId);
        if (changes == 0) {
            throw new NotFoundException("Category not found");
        }
    }

    private boolean isDescendant(long categoryId, long potentialParentId) {
        List<Category> all = categoryRepository.findAllCategories();
        Long currentParent = findParentId(all, potentialParentId);
        while (currentParent != null) {
            if (currentParent == categoryId) {
                return true;
            }
            currentParent = findParentId(all, currentParent);
        }
        return false;
    }

    private Long findParentId(List<Category> all, long id) {
        return all.stream()
                .filter(c -> c.id().equals(id))
                .map(Category::parentId)
                .findFirst()
                .orElse(null);
    }

    public void deleteCategory(long id) {
        if (categoryRepository.hasSubcategories(id)) {
            throw new ValidationException("Cannot delete category with dependent subcategories");
        }
        if (categoryRepository.hasLinkedProducts(id)) {
            throw new ValidationException("Cannot delete category linked to active products");
        }
        int changes = categoryRepository.softDeleteCategory(id);
        if (changes == 0) {
            throw new NotFoundException("Category not found");
        }
    }

    public java.util.Set<Long> getDescendantIds(long categoryId) {
        List<Category> all = categoryRepository.findAllCategories();
        java.util.Set<Long> descendants = new java.util.HashSet<>();
        collectDescendants(categoryId, all, descendants);
        return descendants;
    }

    private void collectDescendants(long parentId, List<Category> all, java.util.Set<Long> descendants) {
        all.stream()
                .filter(c -> parentId == (c.parentId() != null ? c.parentId() : -1))
                .forEach(c -> {
                    descendants.add(c.id());
                    collectDescendants(c.id(), all, descendants);
                });
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

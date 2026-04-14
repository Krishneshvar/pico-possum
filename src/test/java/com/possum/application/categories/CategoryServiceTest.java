package com.possum.application.categories;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Category;
import com.possum.domain.repositories.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
        AuthContext.setCurrentUser(new AuthUser(1L, "Admin", "admin", List.of("admin"), List.of("categories.manage")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch all categories")
    void getAllCategories_success() {
        when(categoryRepository.findAllCategories()).thenReturn(List.of(
            new Category(1L, "Electronics", null, LocalDateTime.now(), null, null)
        ));
        
        List<Category> results = categoryService.getAllCategories();
        assertEquals(1, results.size());
        assertEquals("Electronics", results.get(0).name());
    }

    @Test
    @DisplayName("Should build category tree hierarchy")
    void getCategoriesAsTree_success() {
        List<Category> all = List.of(
            new Category(1L, "Electronics", null, LocalDateTime.now(), null, null),
            new Category(2L, "Laptops", 1L, LocalDateTime.now(), null, null),
            new Category(3L, "Appliances", null, LocalDateTime.now(), null, null)
        );
        when(categoryRepository.findAllCategories()).thenReturn(all);

        List<CategoryService.CategoryTreeNode> tree = categoryService.getCategoriesAsTree();
        
        assertEquals(2, tree.size()); // Root nodes: Electronics, Appliances
        assertEquals(1, tree.get(0).subcategories().size()); // Electronics -> Laptops
    }

    @Test
    @DisplayName("Should create category when allowed")
    void createCategory_success() {
        Category cat = new Category(1L, "Clothes", null, LocalDateTime.now(), null, null);
        when(categoryRepository.insertCategory("Clothes", null)).thenReturn(cat);

        Category result = categoryService.createCategory("Clothes", null);
        assertNotNull(result);
        assertEquals("Clothes", result.name());
    }

    @Test
    @DisplayName("Should throw validation error on empty category name")
    void createCategory_emptyName_fail() {
        assertThrows(ValidationException.class, () -> categoryService.createCategory("", null));
        assertThrows(ValidationException.class, () -> categoryService.createCategory(null, null));
    }

    @Test
    @DisplayName("Should throw authorization error when permission is missing")
    void createCategory_unauthorized_fail() {
        AuthContext.setCurrentUser(new AuthUser(1L, "User", "user", List.of(), List.of()));
        assertThrows(AuthorizationException.class, () -> categoryService.createCategory("Secret", null));
    }

    @Test
    @DisplayName("Should update category successfully")
    void updateCategory_success() {
        when(categoryRepository.updateCategoryById(1L, "New Name", true, 2L)).thenReturn(1);
        
        assertDoesNotThrow(() -> categoryService.updateCategory(1L, "New Name", 2L));
        verify(categoryRepository).updateCategoryById(1L, "New Name", true, 2L);
    }

    @Test
    @DisplayName("Should throw NotFound during update if id doesn't exist")
    void updateCategory_notFound_fail() {
        when(categoryRepository.updateCategoryById(99L, "Name", false, null)).thenReturn(0);
        assertThrows(NotFoundException.class, () -> categoryService.updateCategory(99L, "Name", null));
    }

    @Test
    @DisplayName("Should hard-fail on delete if category not found")
    void deleteCategory_notFound_fail() {
        when(categoryRepository.softDeleteCategory(99L)).thenReturn(0);
        assertThrows(NotFoundException.class, () -> categoryService.deleteCategory(99L));
    }

    @Test
    @DisplayName("Should fetch specific category by id")
    void getCategoryById_success() {
        Category cat = new Category(1L, "Books", null, LocalDateTime.now(), null, null);
        when(categoryRepository.findCategoryById(1L)).thenReturn(Optional.of(cat));

        Category result = categoryService.getCategoryById(1L);
        assertEquals("Books", result.name());
    }
}

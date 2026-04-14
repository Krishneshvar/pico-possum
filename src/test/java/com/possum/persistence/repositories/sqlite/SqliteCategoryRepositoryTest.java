package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteCategoryRepositoryTest {

    private Connection connection;
    private SqliteCategoryRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteCategoryRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                parent_id INTEGER,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT,
                FOREIGN KEY (parent_id) REFERENCES categories (id)
            )
        """);
    }

    @Test
    void insertCategory_validCategory_insertsSuccessfully() {
        Category category = repository.insertCategory("Electronics", null);
        assertNotNull(category);
        assertTrue(category.id() > 0);
        assertEquals("Electronics", category.name());
        assertNull(category.parentId());
    }

    @Test
    void insertCategory_withParent_insertsSuccessfully() {
        Category parent = repository.insertCategory("Electronics", null);
        Category child = repository.insertCategory("Laptops", parent.id());
        
        assertNotNull(child);
        assertEquals("Laptops", child.name());
        assertEquals(parent.id(), child.parentId());
    }

    @Test
    void findCategoryById_found_returnsCategory() {
        Category inserted = repository.insertCategory("Books", null);
        Optional<Category> found = repository.findCategoryById(inserted.id());
        
        assertTrue(found.isPresent());
        assertEquals(inserted.id(), found.get().id());
        assertEquals("Books", found.get().name());
    }

    @Test
    void findCategoryById_notFound_returnsEmpty() {
        Optional<Category> found = repository.findCategoryById(999L);
        assertFalse(found.isPresent());
    }

    @Test
    void findAllCategories_returnsListOfCategories() {
        repository.insertCategory("Cat 1", null);
        repository.insertCategory("Cat 2", null);
        Category c2 = repository.findCategoryById(2).get();
        repository.insertCategory("Subcat 1", c2.id());

        List<Category> all = repository.findAllCategories();
        assertEquals(3, all.size());
    }

    @Test
    void updateCategoryById_validChanges_updatesSuccessfully() {
        Category inserted = repository.insertCategory("Old Name", null);
        
        int rows = repository.updateCategoryById(inserted.id(), "New Name", false, null);
        assertEquals(1, rows);
        
        Optional<Category> updated = repository.findCategoryById(inserted.id());
        assertTrue(updated.isPresent());
        assertEquals("New Name", updated.get().name());
        assertNull(updated.get().parentId());
        
        Category parent = repository.insertCategory("Parent", null);
        int rows2 = repository.updateCategoryById(inserted.id(), null, true, parent.id());
        assertEquals(1, rows2);
        
        Optional<Category> updated2 = repository.findCategoryById(inserted.id());
        assertEquals("New Name", updated2.get().name());
        assertEquals(parent.id(), updated2.get().parentId());
    }

    @Test
    void updateCategoryById_noChanges_returnsZero() {
        Category inserted = repository.insertCategory("Old Name", null);
        int rows = repository.updateCategoryById(inserted.id(), null, false, null);
        assertEquals(0, rows);
    }

    @Test
    void softDeleteCategory_deletesSuccessfully() {
        Category inserted = repository.insertCategory("To Delete", null);
        
        int rows = repository.softDeleteCategory(inserted.id());
        assertEquals(1, rows);
        
        Optional<Category> deleted = repository.findCategoryById(inserted.id());
        assertFalse(deleted.isPresent()); // findCategoryById filters out deleted_at IS NOT NULL
        
        List<Category> all = repository.findAllCategories();
        assertTrue(all.isEmpty()); // findAllCategories filters out deleted_at IS NOT NULL
    }
}

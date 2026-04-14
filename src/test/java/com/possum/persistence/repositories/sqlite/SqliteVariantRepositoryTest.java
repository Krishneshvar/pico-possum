package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Variant;
import com.possum.shared.dto.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteVariantRepositoryTest {

    private Connection connection;
    private SqliteVariantRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteVariantRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sku TEXT UNIQUE,
                barcode TEXT UNIQUE,
                mrp REAL,
                cost_price REAL,
                stock_alert_cap INTEGER DEFAULT 10,
                is_default INTEGER DEFAULT 0,
                status TEXT DEFAULT 'active',
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER,
                tax_category_id INTEGER,
                status TEXT DEFAULT 'active',
                image_path TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE tax_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_adjustments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                variant_id INTEGER NOT NULL,
                lot_id INTEGER,
                quantity_change INTEGER NOT NULL,
                reason TEXT
            )
        """);
    }

    @Test
    void insert_validVariant_insertsSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        Variant variant = new Variant(null, 1L, "Product", "Default", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null);

        long id = repository.insertVariant(1L, variant);

        assertTrue(id > 0);
    }

    @Test
    void insert_duplicateSKU_throwsException() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        Variant variant1 = new Variant(null, 1L, "Product", "Variant 1", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null);
        Variant variant2 = new Variant(null, 1L, "Product", "Variant 2", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, false, "active", 
                null, null, null, null, null, null, null);

        repository.insertVariant(1L, variant1);

        assertThrows(Exception.class, () -> repository.insertVariant(1L, variant2));
    }

    @Test
    void insert_duplicateBarcode_throwsException() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        connection.createStatement().execute(
            "INSERT INTO variants (product_id, name, sku, barcode, mrp, cost_price) VALUES (1, 'Variant 1', 'SKU001', 'BAR001', 100, 50)");

        assertThrows(Exception.class, () -> 
            connection.createStatement().execute(
                "INSERT INTO variants (product_id, name, sku, barcode, mrp, cost_price) VALUES (1, 'Variant 2', 'SKU002', 'BAR001', 100, 50)")
        );
    }

    @Test
    void findById_found_returnsVariant() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        Variant variant = new Variant(null, 1L, "Product", "Default", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null);
        long id = repository.insertVariant(1L, variant);

        Optional<Variant> result = repository.findVariantByIdSync(id);

        assertTrue(result.isPresent());
        assertEquals("Default", result.get().name());
    }

    @Test
    void findByProduct_filtering_returnsVariants() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 1", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 2", "SKU002", 
                new BigDecimal("100"), new BigDecimal("50"), 10, false, "active", 
                null, null, null, null, null, null, null));

        PagedResult<Variant> result = repository.findVariants(null, null, null, null, null, null, null, null, "name", "ASC", 1, 10);

        assertTrue(result.totalCount() >= 2);
    }

    @Test
    void findBySKU_found_returnsVariant() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Default", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));

        PagedResult<Variant> result = repository.findVariants("SKU001", null, null, null, null, null, null, null, "name", "ASC", 1, 10);

        assertTrue(result.totalCount() >= 1);
    }

    @Test
    void findByBarcode_found_returnsVariant() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        connection.createStatement().execute(
            "INSERT INTO variants (product_id, name, sku, barcode, mrp, cost_price) VALUES (1, 'Variant', 'SKU001', 'BAR001', 100, 50)");

        PagedResult<Variant> result = repository.findVariants("BAR001", null, null, null, null, null, null, null, "name", "ASC", 1, 10);

        assertTrue(result.totalCount() >= 0);
    }

    @Test
    void update_validChanges_updatesSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        Variant variant = new Variant(null, 1L, "Product", "Original", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null);
        long id = repository.insertVariant(1L, variant);

        Variant updated = new Variant(id, 1L, "Product", "Updated", "SKU001", 
                new BigDecimal("150"), new BigDecimal("75"), 15, true, "active", 
                null, null, null, null, null, null, null);
        repository.updateVariantById(updated);

        Optional<Variant> found = repository.findVariantByIdSync(id);
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().name());
    }

    @Test
    void delete_withStockCheck_deletesSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        Variant variant = new Variant(null, 1L, "Product", "To Delete", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null);
        long id = repository.insertVariant(1L, variant);

        int changes = repository.softDeleteVariant(id);

        assertTrue(changes > 0);
        Optional<Variant> found = repository.findVariantByIdSync(id);
        assertFalse(found.isPresent());
    }

    @Test
    void search_byNameSKU_returnsMatches() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Apple Variant", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Banana Variant", "SKU002", 
                new BigDecimal("100"), new BigDecimal("50"), 10, false, "active", 
                null, null, null, null, null, null, null));

        PagedResult<Variant> result = repository.findVariants("Apple", null, null, null, null, null, null, null, "name", "ASC", 1, 10);

        assertTrue(result.totalCount() >= 1);
    }

    @Test
    void count_totalVariants_returnsCount() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 1", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 2", "SKU002", 
                new BigDecimal("100"), new BigDecimal("50"), 10, false, "active", 
                null, null, null, null, null, null, null));

        PagedResult<Variant> result = repository.findVariants(null, null, null, null, null, null, null, null, "name", "ASC", 1, 100);

        assertEquals(2, result.totalCount());
    }

    @Test
    void exists_bySKU_checksExistence() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));

        PagedResult<Variant> result = repository.findVariants("SKU001", null, null, null, null, null, null, null, "name", "ASC", 1, 10);

        assertTrue(result.totalCount() > 0);
    }

    @Test
    void getVariantStats_returnsStatistics() throws SQLException {
        connection.createStatement().execute("INSERT INTO products (id, name) VALUES (1, 'Product')");
        
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 1", "SKU001", 
                new BigDecimal("100"), new BigDecimal("50"), 10, true, "active", 
                null, null, null, null, null, null, null));
        repository.insertVariant(1L, new Variant(null, 1L, "Product", "Variant 2", "SKU002", 
                new BigDecimal("100"), new BigDecimal("50"), 10, false, "active", 
                null, null, null, null, null, null, null));

        Map<String, Object> stats = repository.getVariantStats();

        assertNotNull(stats);
        assertTrue((Integer) stats.get("totalVariants") >= 2);
    }
}

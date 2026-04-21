package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.Product;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteProductRepositoryTest {

    private Connection connection;
    private SqliteProductRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteProductRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                category_id INTEGER,
                category_name TEXT,
                tax_rate REAL,
                sku TEXT,
                barcode TEXT,
                mrp REAL,
                cost_price REAL,
                stock_alert_cap INTEGER DEFAULT 10,
                status TEXT DEFAULT 'active',
                image_path TEXT,
                stock INTEGER DEFAULT 0,
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
            CREATE TABLE product_stock_cache (
                product_id INTEGER PRIMARY KEY,
                current_stock INTEGER DEFAULT 0,
                last_updated TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_lots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER,
                quantity INTEGER
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE inventory_adjustments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER,
                quantity_change INTEGER,
                reason TEXT
            )
        """);
    }

    @Test
    void existsBySku_returnsCorrectResult() {
        repository.insertProduct(new Product(null, "P1", null, null, null, BigDecimal.ZERO, "DUPE", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        
        assertTrue(repository.existsBySku("DUPE"));
        assertTrue(repository.existsBySkuExcludeId("DUPE", 999L));
        assertFalse(repository.existsBySkuExcludeId("DUPE", 1L)); // Assuming 1L is the ID of the inserted product
        assertFalse(repository.existsBySku("NONEXISTENT"));
    }

    @Test
    void insert_withInvalidData_throwsException() {
        // Enforced by the record constructor validation
        assertThrows(IllegalArgumentException.class, () -> 
            new Product(null, "", null, null, null, BigDecimal.ZERO, "SKU1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            new Product(null, "Name", null, null, null, BigDecimal.valueOf(-1), "SKU1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null)
        );
    }

    @Test
    void insert_validProduct_insertsSuccessfully() {
        Product product = new Product(null, "Test Product", "Description", null, null, BigDecimal.ZERO, "SKU1", null, new java.math.BigDecimal("100"), new java.math.BigDecimal("80"), 10, "active", null, 0, null, null, null);

        long id = repository.insertProduct(product);

        assertTrue(id > 0);
    }

    @Test
    void findById_found_returnsProduct() {
        Product product = new Product(null, "Find Me", "Description", null, null, BigDecimal.ZERO, "SKU1", null, new java.math.BigDecimal("100"), new java.math.BigDecimal("80"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        Optional<Product> result = repository.findProductById(id);

        assertTrue(result.isPresent());
        assertEquals("Find Me", result.get().name());
        assertEquals("SKU1", result.get().sku());
    }

    @Test
    void findAll_withPagination_returnsPagedResult() {
        repository.insertProduct(new Product(null, "P1", null, null, null, BigDecimal.ZERO, "S1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P2", null, null, null, BigDecimal.ZERO, "S2", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P3", null, null, null, BigDecimal.ZERO, "S3", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));

        ProductFilter filter = new ProductFilter(null, null, null, 0, 2, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(3, result.totalCount());
        assertEquals(2, result.items().size());
    }

    @Test
    void update_validChanges_updatesSuccessfully() {
        Product product = new Product(null, "Original", null, null, null, BigDecimal.ZERO, "S1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        Product updated = new Product(id, "Updated", null, null, null, BigDecimal.ZERO, "S1", null, new java.math.BigDecimal("12"), new java.math.BigDecimal("6"), 15, "inactive", null, 0, null, null, null);
        int changes = repository.updateProductById(id, updated);

        assertTrue(changes > 0);
        Optional<Product> found = repository.findProductById(id);
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().name());
        assertEquals("inactive", found.get().status());
    }

    @Test
    void softDelete_successful() {
        Product product = new Product(null, "Delete Me", null, null, null, BigDecimal.ZERO, "S1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        int changes = repository.softDeleteProduct(id);

        assertTrue(changes > 0);
        assertFalse(repository.findProductById(id).isPresent());
    }

    @Test
    void getProductStats_returnsStatistics() {
        repository.insertProduct(new Product(null, "P1", null, null, null, BigDecimal.ZERO, "S1", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P2", null, null, null, BigDecimal.ZERO, "S2", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P3", null, null, null, BigDecimal.ZERO, "S3", null, new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "inactive", null, 0, null, null, null));

        Map<String, Object> stats = repository.getProductStats();

        assertNotNull(stats);
        assertEquals(3, stats.get("totalProducts"));
        assertEquals(2, stats.get("activeProducts"));
    }
}

package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Product;
import com.possum.domain.repositories.ProductRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
                tax_category_id INTEGER,
                tax_category_name TEXT,
                sku TEXT,
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
            CREATE TABLE tax_categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """);
    }

    @Test
    void insert_validProduct_insertsSuccessfully() {
        Product product = new Product(null, "Test Product", "Description", null, null, null, null, "SKU1", new java.math.BigDecimal("100"), new java.math.BigDecimal("80"), 10, "active", null, 0, null, null, null);

        long id = repository.insertProduct(product);

        assertTrue(id > 0);
    }

    @Test
    void findById_found_returnsProduct() {
        Product product = new Product(null, "Find Me", "Description", null, null, null, null, "SKU1", new java.math.BigDecimal("100"), new java.math.BigDecimal("80"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        Optional<Product> result = repository.findProductById(id);

        assertTrue(result.isPresent());
        assertEquals("Find Me", result.get().name());
        assertEquals("SKU1", result.get().sku());
    }

    @Test
    void findAll_withPagination_returnsPagedResult() {
        repository.insertProduct(new Product(null, "P1", null, null, null, null, null, "S1", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P2", null, null, null, null, null, "S2", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P3", null, null, null, null, null, "S3", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));

        ProductFilter filter = new ProductFilter(null, null, null, null, 0, 2, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(3, result.totalCount());
        assertEquals(2, result.items().size());
    }

    @Test
    void update_validChanges_updatesSuccessfully() {
        Product product = new Product(null, "Original", null, null, null, null, null, "S1", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        Product updated = new Product(id, "Updated", null, null, null, null, null, "S1", new java.math.BigDecimal("12"), new java.math.BigDecimal("6"), 15, "inactive", null, 0, null, null, null);
        int changes = repository.updateProductById(id, updated);

        assertTrue(changes > 0);
        Optional<Product> found = repository.findProductById(id);
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().name());
        assertEquals("inactive", found.get().status());
    }

    @Test
    void softDelete_successful() {
        Product product = new Product(null, "Delete Me", null, null, null, null, null, "S1", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null);
        long id = repository.insertProduct(product);

        int changes = repository.softDeleteProduct(id);

        assertTrue(changes > 0);
        assertFalse(repository.findProductById(id).isPresent());
    }

    @Test
    void getProductStats_returnsStatistics() {
        repository.insertProduct(new Product(null, "P1", null, null, null, null, null, "S1", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P2", null, null, null, null, null, "S2", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "active", null, 0, null, null, null));
        repository.insertProduct(new Product(null, "P3", null, null, null, null, null, "S3", new java.math.BigDecimal("10"), new java.math.BigDecimal("5"), 10, "inactive", null, 0, null, null, null));

        Map<String, Object> stats = repository.getProductStats();

        assertNotNull(stats);
        assertEquals(3, stats.get("totalProducts"));
        assertEquals(2, stats.get("activeProducts"));
    }
}

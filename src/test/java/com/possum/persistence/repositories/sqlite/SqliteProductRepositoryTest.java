package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Product;
import com.possum.persistence.db.ConnectionProvider;
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
            CREATE TABLE variants (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                sku TEXT,
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
    void insert_validProduct_insertsSuccessfully() {
        Product product = new Product(null, "Test Product", "Description", null, null, null, null, "active", null, null, null, null, null);

        long id = repository.insertProduct(product);

        assertTrue(id > 0);
    }

    @Test
    void insert_duplicateName_insertsSuccessfully() {
        Product product1 = new Product(null, "Duplicate Product", "Description 1", null, null, null, null, "active", null, null, null, null, null);
        Product product2 = new Product(null, "Duplicate Product", "Description 2", null, null, null, null, "active", null, null, null, null, null);

        long id1 = repository.insertProduct(product1);
        long id2 = repository.insertProduct(product2);

        assertTrue(id1 > 0);
        assertTrue(id2 > 0);
        assertNotEquals(id1, id2);
    }

    @Test
    void findById_found_returnsProduct() {
        Product product = new Product(null, "Find Me", "Description", null, null, null, null, "active", null, null, null, null, null);
        long id = repository.insertProduct(product);

        Optional<Product> result = repository.findProductById(id);

        assertTrue(result.isPresent());
        assertEquals("Find Me", result.get().name());
    }

    @Test
    void findById_notFound_returnsEmpty() {
        Optional<Product> result = repository.findProductById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void findAll_withPagination_returnsPagedResult() {
        repository.insertProduct(new Product(null, "Product 1", "Desc 1", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 2", "Desc 2", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 3", "Desc 3", null, null, null, null, "active", null, null, null, null, null));

        ProductFilter filter = new ProductFilter(null, null, null, null, 0, 2, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(3, result.totalCount());
        assertEquals(2, result.items().size());
    }

    @Test
    void update_validChanges_updatesSuccessfully() {
        Product product = new Product(null, "Original", "Original Desc", null, null, null, null, "active", null, null, null, null, null);
        long id = repository.insertProduct(product);

        Product updated = new Product(id, "Updated", "Updated Desc", null, null, null, null, "inactive", null, null, null, null, null);
        int changes = repository.updateProductById(id, updated);

        assertTrue(changes > 0);
        Optional<Product> found = repository.findProductById(id);
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().name());
    }

    @Test
    void delete_cascadeToVariants_deletesSuccessfully() throws SQLException {
        Product product = new Product(null, "To Delete", "Description", null, null, null, null, "active", null, null, null, null, null);
        long productId = repository.insertProduct(product);
        
        connection.createStatement().execute(
            "INSERT INTO variants (product_id, name, sku, mrp, cost_price) VALUES (" + productId + ", 'Variant', 'SKU001', 100, 50)");

        int changes = repository.softDeleteProduct(productId);

        assertTrue(changes > 0);
        Optional<Product> found = repository.findProductById(productId);
        assertFalse(found.isPresent());
    }

    @Test
    void findByCategory_filtering_returnsFilteredProducts() throws SQLException {
        connection.createStatement().execute("INSERT INTO categories (id, name) VALUES (1, 'Category 1')");
        connection.createStatement().execute("INSERT INTO categories (id, name) VALUES (2, 'Category 2')");

        repository.insertProduct(new Product(null, "Product 1", "Desc", 1L, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 2", "Desc", 2L, null, null, null, "active", null, null, null, null, null));

        ProductFilter filter = new ProductFilter(null, null, null, List.of(1L), 0, 10, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(1, result.totalCount());
        assertEquals("Product 1", result.items().get(0).name());
    }

    @Test
    void search_byNameDescription_returnsMatches() {
        repository.insertProduct(new Product(null, "Apple Product", "Description", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Banana Product", "Description", null, null, null, null, "active", null, null, null, null, null));

        ProductFilter filter = new ProductFilter("Apple", null, null, null, 0, 10, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(1, result.totalCount());
        assertEquals("Apple Product", result.items().get(0).name());
    }

    @Test
    void count_totalProducts_returnsCount() {
        repository.insertProduct(new Product(null, "Product 1", "Desc", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 2", "Desc", null, null, null, null, "active", null, null, null, null, null));

        ProductFilter filter = new ProductFilter(null, null, null, null, 0, 100, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertEquals(2, result.totalCount());
    }

    @Test
    void exists_byName_checksExistence() {
        repository.insertProduct(new Product(null, "Existing Product", "Description", null, null, null, null, "active", null, null, null, null, null));

        ProductFilter filter = new ProductFilter("Existing Product", null, null, null, 0, 10, "name", "ASC");
        PagedResult<Product> result = repository.findProducts(filter);

        assertTrue(result.totalCount() > 0);
    }

    @Test
    void findWithVariants_joinQuery_returnsProductWithVariants() throws SQLException {
        Product product = new Product(null, "Product With Variants", "Description", null, null, null, null, "active", null, null, null, null, null);
        long productId = repository.insertProduct(product);
        
        connection.createStatement().execute(
            "INSERT INTO variants (product_id, name, sku, mrp, cost_price) VALUES (" + productId + ", 'Variant 1', 'SKU001', 100, 50)");

        Optional<ProductRepository.ProductWithVariants> result = repository.findProductWithVariants(productId);

        assertTrue(result.isPresent());
        assertEquals("Product With Variants", result.get().product().name());
        assertEquals(1, result.get().variants().size());
    }

    @Test
    void getProductStats_returnsStatistics() {
        repository.insertProduct(new Product(null, "Product 1", "Desc", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 2", "Desc", null, null, null, null, "active", null, null, null, null, null));
        repository.insertProduct(new Product(null, "Product 3", "Desc", null, null, null, null, "inactive", null, null, null, null, null));

        Map<String, Object> stats = repository.getProductStats();

        assertNotNull(stats);
        assertEquals(3, stats.get("totalProducts"));
        assertEquals(2, stats.get("activeProducts"));
    }
}

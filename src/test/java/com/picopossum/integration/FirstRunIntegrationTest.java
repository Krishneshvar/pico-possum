package com.picopossum.integration;

import com.picopossum.domain.model.Category;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.shared.dto.CustomerFilter;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.shared.dto.SaleFilter;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * First-run / empty database integration tests.
 * Verifies that a freshly initialised POSSUM database comes with correct
 * seed data (payment methods) and that all "empty" queries return
 * graceful zero/empty results - no null-pointer or SQL errors.
 * Synchronized for Single-User Identity-Agnostic architecture.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstRunIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteProductRepository productRepository;
    private static SqliteCustomerRepository customerRepository;
    private static SqliteSalesRepository salesRepository;
    private static SqliteInventoryRepository inventoryRepository;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-firstrun-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();

        categoryRepository = new SqliteCategoryRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        customerRepository = new SqliteCustomerRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Fresh DB — at least one active payment method exists")
    void freshDatabase_hasDefaultPaymentMethods() {
        List<com.picopossum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
        assertFalse(methods.isEmpty(), "At least one payment method should be seeded (e.g., Cash)");
    }

    @Test
    @Order(2)
    @DisplayName("Fresh DB — findProducts returns empty list, not null or exception")
    void freshDatabase_noProducts_returnsEmptyList() {
        var result = productRepository.findProducts(new ProductFilter(
                null, null, null, 1, 25, "name", "ASC"
        ));
        assertNotNull(result);
        assertNotNull(result.items());
        assertTrue(result.items().isEmpty(), "No products should exist on first run");
        assertEquals(0, result.totalCount());
    }

    @Test
    @Order(3)
    @DisplayName("Fresh DB — findSales returns empty list")
    void freshDatabase_noSales_returnsEmptyList() {
        var result = salesRepository.findSales(new SaleFilter(
                null, null, null, null, null,
                null, null, 1, 25, "sale_date", "DESC", null, null
        ));
        assertNotNull(result);
        assertNotNull(result.items());
        assertEquals(0, result.totalCount());
    }

    @Test
    @Order(4)
    @DisplayName("Fresh DB — findCustomers returns empty list")
    void freshDatabase_noCustomers_returnsEmptyList() {
        var result = customerRepository.findCustomers(new CustomerFilter(
                null, null, null, 1, 25, "name", "ASC"
        ));
        assertNotNull(result);
        assertTrue(result.items().isEmpty(), "No customers should exist on first run");
    }

    @Test
    @Order(5)
    @DisplayName("Fresh DB — getInventoryStats returns non-null map with zero values")
    void freshDatabase_inventoryStats_returnsZeros() {
        var stats = inventoryRepository.getInventoryStats();
        assertNotNull(stats, "Inventory stats should never be null");
        stats.values().forEach(val -> {
            if (val instanceof Number n) {
                assertEquals(0, n.intValue(),
                        "All inventory stats should be 0 on fresh DB, but got: " + val);
            }
        });
    }

    @Test
    @Order(6)
    @DisplayName("Fresh DB — categories list is empty")
    void freshDatabase_noCategories_returnsEmptyList() {
        List<Category> categories = categoryRepository.findAllCategories();
        assertNotNull(categories);
        assertTrue(categories.isEmpty(), "No user-created categories should exist on first run");
    }

    @Test
    @Order(7)
    @DisplayName("Fresh DB — can insert and retrieve a category immediately")
    void freshDatabase_canInsertCategory_afterSetup() {
        String name = "FirstCat-" + UUID.randomUUID();
        Category inserted = categoryRepository.insertCategory(name, null);
        assertNotNull(inserted);
        assertEquals(name, inserted.name());

        var found = categoryRepository.findCategoryById(inserted.id());
        assertTrue(found.isPresent());
        assertEquals(name, found.get().name());
    }

    @Test
    @Order(8)
    @DisplayName("Fresh DB — can insert a product and return non-null ID")
    void freshDatabase_canInsertProduct() {
        Category cat = categoryRepository.insertCategory("SeedCat-" + UUID.randomUUID(), null);
        long productId = productRepository.insertProduct(new Product(
                null, "SeedProduct-" + UUID.randomUUID(), "First product", cat.id(),
                null, BigDecimal.ZERO, "SKU-" + UUID.randomUUID(), null, BigDecimal.TEN, BigDecimal.ONE, 10, "active", null, 0, null, null, null
        ));
        assertTrue(productId > 0, "Product ID should be positive");
    }

    @Test
    @Order(9)
    @DisplayName("Fresh DB — database file exists at expected path")
    void freshDatabase_dbFileExistsOnDisk() {
        Path dbRoot = appPaths.getAppRoot();
        assertTrue(Files.exists(dbRoot), "App data directory should have been created");
    }

    @Test
    @Order(10)
    @DisplayName("Fresh DB — connection is valid")
    void freshDatabase_connectionIsValid() throws SQLException {
        var conn = databaseManager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed(), "Connection should be open");
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete: " + path, ex);
                }
            });
        }
    }
}

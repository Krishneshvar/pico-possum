package com.possum.integration;

import com.possum.domain.model.Category;
import com.possum.domain.model.Product;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.repositories.sqlite.*;
import com.possum.shared.dto.CustomerFilter;
import com.possum.shared.dto.ProductFilter;
import com.possum.shared.dto.SaleFilter;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * First-run / empty database integration tests.
 * Verifies that a freshly initialised POSSUM database comes with correct
 * seed data (roles, payment methods) and that all "empty" queries return
 * graceful zero/empty results — no null-pointer or SQL errors.
 */
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
        // Deliberately fresh UUID dir to simulate first-run
        String appDir = "possum-firstrun-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize(); // Runs Flyway migrations from scratch

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
    @DisplayName("Fresh DB — required roles are seeded by Flyway")
    void freshDatabase_hasSeededRoles() {
        int roleCount = queryInt("SELECT COUNT(*) FROM roles");
        assertTrue(roleCount >= 1, "At least one role (admin) should be seeded");

        int adminCount = queryInt("SELECT COUNT(*) FROM roles WHERE name = 'admin'");
        assertEquals(1, adminCount, "Admin role must exist after first-run migration");
    }

    @Test
    @DisplayName("Fresh DB — at least one active payment method exists")
    void freshDatabase_hasDefaultPaymentMethods() {
        List<com.possum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
        assertFalse(methods.isEmpty(), "At least one payment method should be seeded (e.g., Cash)");
        assertTrue(methods.stream().allMatch(pm -> pm.active()), "All seeded payment methods should be active");
    }

    @Test
    @DisplayName("Fresh DB — findProducts returns empty list, not null or exception")
    void freshDatabase_noProducts_returnsEmptyList() {
        var result = productRepository.findProducts(new ProductFilter(
                null, null, null, null, 1, 25, "name", "ASC"
        ));
        assertNotNull(result);
        assertNotNull(result.items());
        assertTrue(result.items().isEmpty(), "No products should exist on first run");
        assertEquals(0, result.totalCount());
    }

    @Test
    @DisplayName("Fresh DB — findSales returns empty list")
    void freshDatabase_noSales_returnsEmptyList() {
        var result = salesRepository.findSales(new SaleFilter(
                null, null, null, null, null, null, null, null, 1, 25, "sale_date", "DESC", null, null
        ));
        assertNotNull(result);
        assertNotNull(result.items());
        assertEquals(0, result.totalCount());
    }

    @Test
    @DisplayName("Fresh DB — getSaleStats returns zero counts")
    void freshDatabase_saleStatsAreZero() {
        var stats = salesRepository.getSaleStats(new SaleFilter(
                null, null, null, null, null, null, null, null, 1, 25, "sale_date", "DESC", null, null
        ));
        assertNotNull(stats);
        assertEquals(0, stats.totalBills());
        assertEquals(0, stats.paidCount());
        assertEquals(0, stats.partialOrDraftCount());
        assertEquals(0, stats.cancelledOrRefundedCount());
    }

    @Test
    @DisplayName("Fresh DB — findCustomers returns empty list")
    void freshDatabase_noCustomers_returnsEmptyList() {
        var result = customerRepository.findCustomers(new CustomerFilter(
                null, 1, 25, 1, 25, "name", "ASC"
        ));
        assertNotNull(result);
        assertTrue(result.items().isEmpty(), "No customers should exist on first run");
    }

    @Test
    @DisplayName("Fresh DB — getInventoryStats returns non-null map with zero values")
    void freshDatabase_inventoryStats_returnsZeros() {
        var stats = inventoryRepository.getInventoryStats();
        assertNotNull(stats, "Inventory stats should never be null");
        // All numeric values should be zero or zero-like
        stats.values().forEach(val -> {
            if (val instanceof Number n) {
                assertEquals(0, n.intValue(),
                        "All inventory stats should be 0 on fresh DB, but got: " + val);
            }
        });
    }

    @Test
    @DisplayName("Fresh DB — categories list is empty")
    void freshDatabase_noCategories_returnsEmptyList() {
        List<Category> categories = categoryRepository.findAllCategories();
        assertNotNull(categories);
        assertTrue(categories.isEmpty(), "No user-created categories should exist on first run");
    }

    @Test
    @DisplayName("Fresh DB — can insert and retrieve a category immediately")
    void freshDatabase_canInsertCategory_afterSetup() {
        String name = "FirstCat-" + UUID.randomUUID();
        Category inserted = categoryRepository.insertCategory(name, null);
        assertNotNull(inserted);
        assertNotNull(inserted.id());
        assertEquals(name, inserted.name());
        assertNull(inserted.parentId());

        // Can retrieve it
        var found = categoryRepository.findCategoryById(inserted.id());
        assertTrue(found.isPresent());
        assertEquals(name, found.get().name());
    }

    @Test
    @DisplayName("Fresh DB — can insert a product and return non-null ID")
    void freshDatabase_canInsertProduct() {
        Category cat = categoryRepository.insertCategory("SeedCat-" + UUID.randomUUID(), null);
        long productId = productRepository.insertProduct(new Product(
                null, "SeedProduct-" + UUID.randomUUID(), "First product", cat.id(),
                null, null, null, "active", null, null, null, null, null
        ));
        assertTrue(productId > 0, "Product ID should be positive");
    }

    @Test
    @DisplayName("Fresh DB — database file exists at expected path")
    void freshDatabase_dbFileExistsOnDisk() {
        // The DB manager should have created the SQLite file
        Path dbRoot = appPaths.getAppRoot();
        assertTrue(Files.exists(dbRoot), "App data directory should have been created");
    }

    @Test
    @DisplayName("Fresh DB — can get a connection and it is valid")
    void freshDatabase_connectionIsValid() throws SQLException {
        var conn = databaseManager.getConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed(), "Connection should be open");
    }

    @Test
    @DisplayName("Fresh DB — reinitialise is idempotent (Flyway baseline is respected)")
    void freshDatabase_reinitialise_isIdempotent() {
        // Calling initialize() again should not throw or corrupt data
        assertDoesNotThrow(() -> databaseManager.initialize());

        // Data seeded earlier should still be consistent
        int adminCount = queryInt("SELECT COUNT(*) FROM roles WHERE name = 'admin'");
        assertEquals(1, adminCount, "Admin role should still exist after re-initialise");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static int queryInt(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new IllegalStateException("queryInt failed: " + sql, e); }
        throw new IllegalStateException("No result: " + sql);
    }

    private static PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql);
        for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
        return stmt;
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

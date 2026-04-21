package com.picopossum.integration;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.sales.*;
import com.picopossum.application.sales.dto.*;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
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
 * Performance-oriented integration test for the inventory system.
 * Verifies that stock deductions and history logging remain fast and correct
 * under sequential high-volume operations (1000+ sales).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryPerformanceIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteProductRepository productRepository;
    private static SqliteCategoryRepository categoryRepository;

    private static long testProductId;
    private static long testUserId;
    private static long cashPaymentMethodId;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-perf-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        JsonService jsonService = new JsonService();
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);

        categoryRepository = new SqliteCategoryRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore, new com.picopossum.domain.services.StockManager());

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository, productRepository, customerRepository, 
                auditRepository, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager, 
                jsonService, settingsStore, invoiceNumberService, returnsRepository);

        User u = userRepository.insertUser(
                new User(null, "Perf Tester", "perf-" + UUID.randomUUID(), "hash", true, null, null, null)
        );
        testUserId = u.id();
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testProductId = seedProductWithStock(categoryRepository, productRepository, 100000);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Perf Tester", "perf"));
    }

    @Test
    @Order(1)
    @DisplayName("Performance: Create 1000 sales sequentially — verifies response time and final stock")
    void performance_create1000SalesSequentially() {
        int initialStock = inventoryService.getProductStock(testProductId);
        int iterations = 1000;
        BigDecimal unitPrice = new BigDecimal("10.00");

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            salesService.createSale(new CreateSaleRequest(
                    List.of(new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, unitPrice)),
                    null, BigDecimal.ZERO,
                    List.of(new PaymentRequest(unitPrice, cashPaymentMethodId))
            ), testUserId);
        }
        long end = System.currentTimeMillis();
        long duration = end - start;

        System.out.println("Created 1000 sales in " + duration + "ms (" + (duration / iterations) + "ms/sale)");

        int finalStock = inventoryService.getProductStock(testProductId);
        assertEquals(initialStock - iterations, finalStock);

        int auditCount = queryInt("SELECT COUNT(*) FROM audit_log WHERE table_name = 'sales'");
        assertTrue(auditCount >= iterations);

        assertTrue(duration < 45000, "Performance threshold exceeded: 1000 sales took " + duration + "ms");
    }

    private static long seedProductWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("PerfCat-" + UUID.randomUUID(), null).id();
        long productId = prodRepo.insertProduct(new Product(
            null, "PerfProd-" + UUID.randomUUID(), "desc", catId, null, "PSKU-" + UUID.randomUUID(),
            new BigDecimal("100.00"), new BigDecimal("60.00"), 0, "active", null, 5, null, null, null
        ));
        seedInventory(productId, qty);
        return productId;
    }

    private static void seedInventory(long productId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (product_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("60.00"));
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Seed lot failed", e); }

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_adjustments (product_id, lot_id, quantity_change, reason, adjusted_by, adjusted_at) " +
                "VALUES (?, ?, ?, 'correction', ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setLong(2, queryLong("SELECT id FROM inventory_lots WHERE product_id = ? ORDER BY id DESC LIMIT 1", productId));
            stmt.setInt(3, quantity);
            stmt.setLong(4, 1);
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Seed adjustment failed", e); }
    }

    private static long getOrSeedPaymentMethod() {
        List<com.picopossum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
        if (!methods.isEmpty()) return methods.get(0).id();
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO payment_methods (name, code, is_active) VALUES ('Cash', 'CA', 1) RETURNING id")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) { throw new IllegalStateException("Seed PM failed", e); }
        throw new IllegalStateException("No ID returned");
    }

    private static long queryLong(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new IllegalStateException("queryLong failed: " + sql, e); }
        throw new IllegalStateException("No result: " + sql);
    }

    private static int queryInt(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new IllegalStateException("queryInt failed", e); }
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

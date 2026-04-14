package com.possum.integration;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.inventory.ProductFlowService;
import com.possum.application.sales.*;
import com.possum.application.sales.dto.*;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.db.TransactionManager;
import com.possum.persistence.repositories.sqlite.*;
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

    private static long testVariantId;
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

        SqliteCategoryRepository categoryRepository = new SqliteCategoryRepository(databaseManager);
        SqliteProductRepository productRepository = new SqliteProductRepository(databaseManager);
        SqliteVariantRepository variantRepository = new SqliteVariantRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore, new com.possum.domain.services.StockManager());

        SqliteTaxRepository taxRepository = new SqliteTaxRepository(databaseManager);
        TaxEngine taxEngine = new TaxEngine(taxRepository, jsonService);
        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository,  variantRepository,  productRepository,  customerRepository, 
                auditRepository,  inventoryService,  taxEngine, new com.possum.domain.services.SaleCalculator( taxEngine),  paymentService,  transactionManager, 
                jsonService,  settingsStore,  invoiceNumberService);

        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        User u = userRepository.insertUserWithRoles(
                new User(null, "Perf Tester", "perf-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of(roleId)
        );
        testUserId = u.id();
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testVariantId = seedVariantWithStock(categoryRepository, productRepository, 100000);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Perf Tester", "perf",
                List.of("admin"), List.of("sales:create", "sales:manage")));
    }

    @Test
    @Order(1)
    @DisplayName("Performance: Create 1000 sales sequentially — verifies response time and final stock")
    void performance_create1000SalesSequentially() {
        int initialStock = inventoryService.getVariantStock(testVariantId);
        int iterations = 1000;
        BigDecimal unitPrice = new BigDecimal("10.00");

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            salesService.createSale(new CreateSaleRequest(
                    List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, unitPrice)),
                    null, BigDecimal.ZERO,
                    List.of(new PaymentRequest(unitPrice, cashPaymentMethodId))
            ), testUserId);
        }
        long end = System.currentTimeMillis();
        long duration = end - start;

        System.out.println("Created 1000 sales in " + duration + "ms (" + (duration / iterations) + "ms/sale)");

        // Verify final stock
        int finalStock = inventoryService.getVariantStock(testVariantId);
        assertEquals(initialStock - iterations, finalStock);

        // Verify audit logs exist
        int auditCount = queryInt("SELECT COUNT(*) FROM audit_log WHERE table_name = 'sales'");
        assertTrue(auditCount >= iterations);

        // Limit check: 1000 sales
        assertTrue(duration < 45000, "Performance threshold exceeded: 1000 sales took " + duration + "ms");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static long seedVariantWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("PerfCat-" + UUID.randomUUID(), null).id();
        long prodId = prodRepo.insertProduct(new Product(
                null, "PerfProd-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "PSKU-" + UUID.randomUUID());
        seedInventory(variantId, qty);
        return variantId;
    }

    private static long insertVariant(long productId, String sku) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO variants (product_id, name, sku, mrp, cost_price, is_default, status) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            stmt.setLong(1, productId);
            stmt.setString(2, "Default");
            stmt.setString(3, sku);
            stmt.setBigDecimal(4, new BigDecimal("100.00"));
            stmt.setBigDecimal(5, new BigDecimal("60.00"));
            stmt.setInt(6, 1);
            stmt.setString(7, "active");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) { throw new IllegalStateException("insertVariant failed", e); }
        throw new IllegalStateException("No ID returned");
    }

    private static void seedInventory(long variantId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (variant_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, variantId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("60.00"));
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Seed lot failed", e); }

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_adjustments (variant_id, lot_id, quantity_change, reason, adjusted_by, adjusted_at) " +
                "VALUES (?, ?, ?, 'correction', ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, variantId);
            stmt.setLong(2, queryLong("SELECT id FROM inventory_lots WHERE variant_id = ? ORDER BY id DESC LIMIT 1", variantId));
            stmt.setInt(3, quantity);
            stmt.setLong(4, 1);
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Seed adjustment failed", e); }
    }

    private static long getOrSeedPaymentMethod() {
        List<com.possum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
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

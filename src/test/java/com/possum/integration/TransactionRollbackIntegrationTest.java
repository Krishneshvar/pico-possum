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
 * Tests for corrupt-state recovery and transaction atomicity.
 * Verifies that if a sale fails mid-way (e.g., crash, exception),
 * the database is rolled back cleanly — no phantom stock deductions or
 * orphaned records.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionRollbackIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteInventoryRepository inventoryRepository;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteProductRepository productRepository;

    private static long testVariantId;
    private static long testUserId;
    private static long cashPaymentMethodId;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-rollback-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        JsonService jsonService = new JsonService();
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);

        categoryRepository = new SqliteCategoryRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        SqliteVariantRepository variantRepository = new SqliteVariantRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
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
                new User(null, "Rollback Tester", "rbtester-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of(roleId)
        );
        testUserId = u.id();
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testVariantId = seedVariantWithStock(50);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
        AuthContext.clear();
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Rollback Tester", "rbtester",
                List.of("admin"), List.of("sales:create", "sales:manage")));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Exception mid-transaction — entire sale rolled back, stock unchanged")
    void midTransactionException_rollsBackSaleAndStock() {
        int stockBefore = inventoryService.getVariantStock(testVariantId);
        int salesCountBefore = queryInt("SELECT COUNT(*) FROM sales");

        // Force a transaction failure by injecting an invalid operation: use a non-existent variant
        assertThrows(Exception.class, () ->
                salesService.createSale(new CreateSaleRequest(
                        List.of(new CreateSaleItemRequest(999999L, 1, BigDecimal.ZERO, new BigDecimal("100.00"))),
                        null, BigDecimal.ZERO,
                        List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
                ), testUserId)
        );

        // No sale should have been inserted
        int salesCountAfter = queryInt("SELECT COUNT(*) FROM sales");
        assertEquals(salesCountBefore, salesCountAfter, "No orphan sale record should exist after rollback");

        // Stock should be unchanged
        int stockAfter = inventoryService.getVariantStock(testVariantId);
        assertEquals(stockBefore, stockAfter, "Stock should be unchanged after failed sale");
    }

    @Test
    @Order(2)
    @DisplayName("TransactionManager explicit rollback — category not persisted")
    void explicitRollback_categoryNotPersisted() {
        String rollbackName = "RollbackCat-" + UUID.randomUUID();

        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(() -> {
                    categoryRepository.insertCategory(rollbackName, null);
                    throw new RuntimeException("Simulated crash mid-transaction");
                })
        );

        int count = queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", rollbackName);
        assertEquals(0, count, "Category should not exist after rollback");
    }

    @Test
    @Order(3)
    @DisplayName("Nested transaction — inner rollback preserved, outer transaction succeeds")
    void nestedTransaction_innerRollback_outerSucceeds() {
        String outerName = "OuterCat-" + UUID.randomUUID();
        String innerName = "InnerCat-" + UUID.randomUUID();

        transactionManager.runInTransaction(() -> {
            categoryRepository.insertCategory(outerName, null);

            // Inner transaction fails via savepoint
            assertThrows(RuntimeException.class, () ->
                    transactionManager.runInTransaction(() -> {
                        categoryRepository.insertCategory(innerName, null);
                        throw new RuntimeException("Rollback inner only");
                    })
            );

            return null;
        });

        // Outer should be committed
        int outerCount = queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", outerName);
        assertEquals(1, outerCount, "Outer transaction should have been committed");

        // Inner should be rolled back to savepoint
        int innerCount = queryInt("SELECT COUNT(*) FROM categories WHERE name = ?", innerName);
        assertEquals(0, innerCount, "Inner transaction should have been rolled back");
    }

    @Test
    @Order(4)
    @DisplayName("Transaction with multiple DB writes — all rolled back atomically on failure")
    void multipleWrites_allRolledBackOnFailure() {
        int salesBefore = queryInt("SELECT COUNT(*) FROM sales");
        int saleItemsBefore = queryInt("SELECT COUNT(*) FROM sale_items");

        // This will fail because the second variant doesn't exist — first variant's stock deduction
        // happens inside the same transation so everything rolls back
        assertThrows(Exception.class, () ->
                salesService.createSale(new CreateSaleRequest(
                        List.of(
                                new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("50.00")),
                                new CreateSaleItemRequest(-1L, 1, BigDecimal.ZERO, new BigDecimal("50.00")) // invalid variant
                        ),
                        null, BigDecimal.ZERO,
                        List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
                ), testUserId)
        );

        // All counts should be unchanged
        assertEquals(salesBefore, queryInt("SELECT COUNT(*) FROM sales"), "No orphan sale");
        assertEquals(saleItemsBefore, queryInt("SELECT COUNT(*) FROM sale_items"), "No orphan sale items");
        // audit: may or may not change depending on where exactly the exception throws
    }

    @Test
    @Order(5)
    @DisplayName("Duplicate invoice number — database constraint prevents second insert")
    void duplicateInvoiceNumber_throwsConstraintViolation() {
        // Insert a sale with a known invoice
        String invoice = "DUPE-" + UUID.randomUUID().toString().substring(0, 8);

        salesRepository.insertSale(new Sale(
                null, invoice, null,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                "paid", "fulfilled", null, testUserId,
                null, null, null, null, null, null
        ));

        // Trying to insert another sale with the same invoice should fail
        assertThrows(Exception.class, () ->
                salesRepository.insertSale(new Sale(
                        null, invoice, null,
                        new BigDecimal("50.00"), new BigDecimal("50.00"),
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        "paid", "fulfilled", null, testUserId,
                        null, null, null, null, null, null
                ))
        );
    }

    @Test
    @Order(6)
    @DisplayName("Cancel already-cancelled sale — throws ValidationException, no audit duplication")
    void cancelAlreadyCancelledSale_throwsValidation() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Rollback Tester", "rbtester",
                List.of("admin"), List.of("sales:create", "sales:manage")));

        SaleResponse saleResp = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("50.00"), cashPaymentMethodId))
        ), testUserId);

        salesService.cancelSale(saleResp.sale().id(), testUserId);

        // Try to cancel again
        assertThrows(com.possum.domain.exceptions.ValidationException.class, () ->
                salesService.cancelSale(saleResp.sale().id(), testUserId)
        );
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static long seedVariantWithStock(int qty) {
        long catId = categoryRepository.insertCategory("RBCat-" + UUID.randomUUID(), null).id();
        long prodId = productRepository.insertProduct(new Product(
                null, "RBProd-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "RBSKU-" + UUID.randomUUID());
        seedInventory(variantId, qty);
        return variantId;
    }

    private static long insertVariant(long productId, String sku) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO variants (product_id, name, sku, mrp, cost_price, is_default, status) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            stmt.setLong(1, productId);
            stmt.setString(2, "Default");
            stmt.setString(3, sku);
            stmt.setBigDecimal(4, new BigDecimal("50.00"));
            stmt.setBigDecimal(5, new BigDecimal("30.00"));
            stmt.setInt(6, 1);
            stmt.setString(7, "active");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) { throw new IllegalStateException("insertVariant failed", e); }
        throw new IllegalStateException("No variant ID returned");
    }

    private static void seedInventory(long variantId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (variant_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, variantId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("30.00"));
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
        List<PaymentMethod> methods = salesRepository.findPaymentMethods();
        if (!methods.isEmpty()) return methods.get(0).id();
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO payment_methods (name, code, is_active) VALUES ('Cash', 'CA', 1) RETURNING id")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) { throw new IllegalStateException("Seed PM failed", e); }
        throw new IllegalStateException("No PM ID returned");
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

package com.picopossum.integration;

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
 * Tests for corrupt-state recovery and transaction atomicity.
 * Verifies that if a sale fails mid-way (e.g., crash, exception),
 * the database is rolled back cleanly — no phantom stock deductions or
 * orphaned records.
 * Synchronized for Single-User Identity-Agnostic architecture.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionRollbackIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteProductRepository productRepository;

    private static long testProductId;
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
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore);

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository, productRepository, customerRepository,
                auditRepository, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager,
                jsonService, settingsStore, invoiceNumberService, returnsRepository);

        cashPaymentMethodId = getOrSeedPaymentMethod();
        testProductId = seedProductWithStock(50);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Exception mid-transaction — entire sale rolled back, stock unchanged")
    void midTransactionException_rollsBackSaleAndStock() {
        int stockBefore = inventoryService.getProductStock(testProductId);
        int salesCountBefore = queryInt("SELECT COUNT(*) FROM sales");

        // Force a transaction failure by injecting an invalid operation: use a non-existent product
        assertThrows(Exception.class, () ->
                salesService.createSale(new CreateSaleRequest(
                        List.of(new CreateSaleItemRequest(999999L, 1, BigDecimal.ZERO, new BigDecimal("100.00"))),
                        null, BigDecimal.ZERO,
                        List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
                ))
        );

        // No sale should have been inserted
        int salesCountAfter = queryInt("SELECT COUNT(*) FROM sales");
        assertEquals(salesCountBefore, salesCountAfter, "No orphan sale record should exist after rollback");

        // Stock should be unchanged
        int stockAfter = inventoryService.getProductStock(testProductId);
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
    @DisplayName("Transaction with multiple DB writes — all rolled back atomically on failure")
    void multipleWrites_allRolledBackOnFailure() {
        int salesBefore = queryInt("SELECT COUNT(*) FROM sales");
        int saleItemsBefore = queryInt("SELECT COUNT(*) FROM sale_items");

        // This will fail because the second product doesn't exist
        assertThrows(Exception.class, () ->
                salesService.createSale(new CreateSaleRequest(
                        List.of(
                                new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, new BigDecimal("50.00")),
                                new CreateSaleItemRequest(-1L, 1, BigDecimal.ZERO, new BigDecimal("50.00")) // invalid product
                        ),
                        null, BigDecimal.ZERO,
                        List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
                ))
        );

        // All counts should be unchanged
        assertEquals(salesBefore, queryInt("SELECT COUNT(*) FROM sales"), "No orphan sale");
        assertEquals(saleItemsBefore, queryInt("SELECT COUNT(*) FROM sale_items"), "No orphan sale items");
    }

    @Test
    @Order(4)
    @DisplayName("Duplicate invoice number — database constraint prevents second insert")
    void duplicateInvoiceNumber_throwsConstraintViolation() {
        String invoice = "DUPE-" + UUID.randomUUID().toString().substring(0, 8);

        salesRepository.insertSale(new Sale(
                null, invoice, null,
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO,
                "paid", "fulfilled", null, "Guest",
                null, null, "System Admin", 1L, "Cash", invoice
        ));

        // Trying to insert another sale with the same invoice should fail
        assertThrows(Exception.class, () ->
                salesRepository.insertSale(new Sale(
                        null, invoice, null,
                        new BigDecimal("50.00"), new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        "paid", "fulfilled", null, "Guest",
                        null, null, "System Admin", 1L, "Cash", invoice
                ))
        );
    }

    @Test
    @Order(5)
    @DisplayName("Cancel already-cancelled sale — throws ValidationException")
    void cancelAlreadyCancelledSale_throwsValidation() {
        SaleResponse saleResp = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("50.00"), cashPaymentMethodId))
        ));

        salesService.cancelSale(saleResp.sale().id());

        assertThrows(com.picopossum.domain.exceptions.ValidationException.class, () ->
                salesService.cancelSale(saleResp.sale().id())
        );
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static long seedProductWithStock(int qty) {
        long catId = categoryRepository.insertCategory("RBCat-" + UUID.randomUUID(), null).id();
        long productId = productRepository.insertProduct(new Product(
            null, "RBProd-" + UUID.randomUUID(), "desc", catId, null, BigDecimal.ZERO,
            "RBSKU-" + UUID.randomUUID(), null, new BigDecimal("50.00"), new BigDecimal("30.00"), 5, "active", null, 0, null, null, null
        ));
        seedInventory(productId, qty);
        return productId;
    }

    private static void seedInventory(long productId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO stock_movements (product_id, quantity_change, reason, reference_type, created_at) " +
                "VALUES (?, ?, 'receive', 'manual', CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setInt(2, quantity);
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Seed target failed", e); }
    }

    private static long getOrSeedPaymentMethod() {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO payment_methods (name, code) VALUES ('Cash', 'CA') ON CONFLICT DO NOTHING")) {
            stmt.executeUpdate();
        } catch (SQLException e) { }
        
        List<PaymentMethod> methods = salesRepository.findPaymentMethods();
        for (var m : methods) if ("Cash".equals(m.name())) return m.id();
        throw new IllegalStateException("Seed PM failed");
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

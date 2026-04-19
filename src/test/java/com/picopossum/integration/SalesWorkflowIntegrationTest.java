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
 * End-to-end integration test for the full sale workflow:
 * create product → apply tax/discount → collect payment → deduct stock → audit log.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SalesWorkflowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteInventoryRepository inventoryRepository;
    private static SqliteProductRepository productRepository;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteAuditRepository auditRepository;

    private static long testProductId;
    private static long testUserId;
    private static long cashPaymentMethodId;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-sales-e2e-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        JsonService jsonService = new JsonService();
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);

        categoryRepository = new SqliteCategoryRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        auditRepository = new SqliteAuditRepository(databaseManager);
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore, new com.picopossum.domain.services.StockManager());

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        salesService = new SalesService(salesRepository, productRepository, customerRepository,
                auditRepository, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager,
                jsonService, settingsStore, invoiceNumberService);

        // Seed test data
        testUserId = seedUser(userRepository);
        testProductId = seedProductWithStock();
        cashPaymentMethodId = seedPaymentMethod();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
        AuthContext.clear();
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Test Cashier", "cashier",
                List.of("admin"), List.of("sales.create", "sales.manage")));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Full sale happy path — creates sale, deducts stock, logs audit")
    void fullSaleHappyPath_createsSale_deductsStock_logsAudit() {
        int stockBefore = inventoryService.getProductStock(testProductId);

        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 2, BigDecimal.ZERO, new BigDecimal("100.00"))),
                null,
                BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("200.00"), cashPaymentMethodId))
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        assertNotNull(response);
        assertNotNull(response.sale());
        assertEquals("paid", response.sale().status());
        assertEquals(0, new BigDecimal("200.00").compareTo(response.sale().totalAmount()));
        assertEquals(1, response.items().size());
        assertEquals(2, response.items().get(0).quantity());

        int stockAfter = inventoryService.getProductStock(testProductId);
        assertEquals(stockBefore - 2, stockAfter);

        long saleId = response.sale().id();
        int auditCount = queryInt("SELECT COUNT(*) FROM audit_log WHERE table_name = 'sales' AND row_id = ?", saleId);
        assertEquals(1, auditCount);
    }

    @Test
    @Order(2)
    @DisplayName("Cancel sale — stock is restored and status updated")
    void cancelSale_restoresStock_updatesStatus() {
        int stockBefore = inventoryService.getProductStock(testProductId);
        SaleResponse response = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, new BigDecimal("100.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
        ), testUserId);

        int stockAfterSale = inventoryService.getProductStock(testProductId);
        assertEquals(stockBefore - 1, stockAfterSale);

        salesService.cancelSale(response.sale().id(), testUserId);

        Sale cancelled = salesRepository.findSaleById(response.sale().id()).orElseThrow();
        assertEquals("cancelled", cancelled.status());

        int stockAfterCancel = inventoryService.getProductStock(testProductId);
        assertEquals(stockBefore, stockAfterCancel);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static long seedUser(SqliteUserRepository userRepository) {
        User user = userRepository.insertUserWithRoles(
                new User(null, "Test Cashier", "cashier-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of()
        );
        return user.id();
    }

    private static long seedProductWithStock() {
        long catId = categoryRepository.insertCategory("Cat-" + UUID.randomUUID(), null).id();
        Product p = new Product(null, "Product-" + UUID.randomUUID(), "desc", catId, null, "SKU-" + UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("60.00"), 10, "active", null, 0, null, null, null);
        long productId = productRepository.insertProduct(p);
        seedInventory(productId, 50);
        return productId;
    }

    private static void seedInventory(long productId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (product_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("60.00"));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed inventory", e);
        }

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_adjustments (product_id, lot_id, quantity_change, reason, adjusted_by, adjusted_at) " +
                "VALUES (?, ?, ?, 'correction', ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setLong(2, queryLong("SELECT id FROM inventory_lots WHERE product_id = ? ORDER BY id DESC LIMIT 1", productId));
            stmt.setInt(3, quantity);
            stmt.setLong(4, 1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed inventory adjustment", e);
        }
    }

    private static long seedPaymentMethod() {
        List<com.picopossum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
        if (!methods.isEmpty()) return methods.get(0).id();

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO payment_methods (name, code, is_active) VALUES ('Cash', 'CA', 1) RETURNING id")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed payment method", e);
        }
        throw new IllegalStateException("Failed to seed payment method");
    }

    private static long queryLong(String sql, Object... params) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("queryLong failed: " + sql, e);
        }
        throw new IllegalStateException("No result: " + sql);
    }

    private static int queryInt(String sql, Object... params) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("queryInt failed", e);
        }
        throw new IllegalStateException("No result: " + sql);
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

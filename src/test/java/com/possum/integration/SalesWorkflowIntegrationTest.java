package com.possum.integration;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.inventory.ProductFlowService;
import com.possum.application.sales.*;
import com.possum.application.sales.dto.*;
import com.possum.domain.exceptions.InsufficientStockException;
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
 * End-to-end integration test for the full sale workflow:
 * add variant → apply tax/discount → collect payment → deduct stock → audit log.
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
    private static SqliteVariantRepository variantRepository;
    private static SqliteProductRepository productRepository;
    private static SqliteCategoryRepository categoryRepository;
    private static SqliteAuditRepository auditRepository;

    private static long testVariantId;
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
        variantRepository = new SqliteVariantRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        auditRepository = new SqliteAuditRepository(databaseManager);
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore, new com.possum.domain.services.StockManager());

        SqliteTaxRepository taxRepository = new SqliteTaxRepository(databaseManager);
        TaxEngine taxEngine = new TaxEngine(taxRepository, jsonService);
        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        salesService = new SalesService(salesRepository,  variantRepository,  productRepository,  customerRepository, 
                auditRepository,  inventoryService,  taxEngine, new com.possum.domain.services.SaleCalculator( taxEngine),  paymentService,  transactionManager, 
                jsonService,  settingsStore,  invoiceNumberService);

        // Seed test data
        testUserId = seedUser(userRepository);
        testVariantId = seedVariantWithStock();
        cashPaymentMethodId = seedPaymentMethod();

        // Disable inventory restrictions so tests don't fail on stock checks from settings file
        settingsStore.loadGeneralSettings(); // ensures settings file is created with defaults
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
                List.of("admin"), List.of("sales:create", "sales:manage")));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Full sale happy path — creates sale, deducts stock, logs audit")
    void fullSaleHappyPath_createsSale_deductsStock_logsAudit() {
        int stockBefore = inventoryService.getVariantStock(testVariantId);

        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 2, BigDecimal.ZERO, new BigDecimal("100.00"))),
                null,
                BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("200.00"), cashPaymentMethodId))
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        // Sale created
        assertNotNull(response);
        assertNotNull(response.sale());
        assertNotNull(response.sale().invoiceNumber());
        assertEquals("paid", response.sale().status());
        assertEquals("fulfilled", response.sale().fulfillmentStatus());

        // Correct total
        assertEquals(0, new BigDecimal("200.00").compareTo(response.sale().totalAmount()));

        // Sale items persisted
        assertFalse(response.items().isEmpty());
        assertEquals(1, response.items().size());
        assertEquals(2, response.items().get(0).quantity());

        // Transactions persisted
        assertEquals(1, response.transactions().size());

        // Stock deducted
        int stockAfter = inventoryService.getVariantStock(testVariantId);
        assertEquals(stockBefore - 2, stockAfter);

        // Audit logged
        long saleId = response.sale().id();
        int auditCount = queryInt("SELECT COUNT(*) FROM audit_log WHERE table_name = 'sales' AND row_id = ?", saleId);
        assertEquals(1, auditCount);
    }

    @Test
    @Order(2)
    @DisplayName("Sale with global discount — total is correctly reduced")
    void saleWithDiscount_reducesTotal() {
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("100.00"))),
                null,
                new BigDecimal("10.00"), // global discount
                List.of(new PaymentRequest(new BigDecimal("90.00"), cashPaymentMethodId))
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        assertNotNull(response.sale());
        // Total should be 100 - 10 = 90 (no tax in test DB)
        assertEquals(0, new BigDecimal("90.00").compareTo(response.sale().totalAmount()));
    }

    @Test
    @Order(3)
    @DisplayName("Sale with multiple items — all items deducted from stock")
    void saleWithMultipleItems_deductsAllFromStock() {
        long secondVariantId = seedAnotherVariantWithStock(5);
        int stock1Before = inventoryService.getVariantStock(testVariantId);
        int stock2Before = inventoryService.getVariantStock(secondVariantId);

        CreateSaleRequest request = new CreateSaleRequest(
                List.of(
                        new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("50.00")),
                        new CreateSaleItemRequest(secondVariantId, 2, BigDecimal.ZERO, new BigDecimal("30.00"))
                ),
                null,
                BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("110.00"), cashPaymentMethodId))
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        assertEquals(2, response.items().size());
        assertEquals(stock1Before - 1, inventoryService.getVariantStock(testVariantId));
        assertEquals(stock2Before - 2, inventoryService.getVariantStock(secondVariantId));
    }

    @Test
    @Order(4)
    @DisplayName("Partial payment — sale status is partially_paid")
    void partialPayment_saleIsPartiallyPaid() {
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("200.00"))),
                null,
                BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId)) // only half paid
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        assertEquals("partially_paid", response.sale().status());
    }

    @Test
    @Order(5)
    @DisplayName("Sale without payment — sale status is draft")
    void noPayment_saleIsDraft() {
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null,
                BigDecimal.ZERO,
                List.of() // no payment
        );

        SaleResponse response = salesService.createSale(request, testUserId);

        assertEquals("draft", response.sale().status());
    }

    @Test
    @Order(6)
    @DisplayName("Cancel sale — stock is restored and status updated")
    void cancelSale_restoresStock_updatesStatus() {
        // Create a sale first
        int stockBefore = inventoryService.getVariantStock(testVariantId);
        SaleResponse response = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("100.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
        ), testUserId);

        int stockAfterSale = inventoryService.getVariantStock(testVariantId);
        assertEquals(stockBefore - 1, stockAfterSale);

        // Cancel it
        salesService.cancelSale(response.sale().id(), testUserId);

        Sale cancelled = salesRepository.findSaleById(response.sale().id()).orElseThrow();
        assertEquals("cancelled", cancelled.status());

        // Stock restored
        int stockAfterCancel = inventoryService.getVariantStock(testVariantId);
        assertEquals(stockBefore, stockAfterCancel);
    }

    @Test
    @Order(7)
    @DisplayName("Invoice number is unique per sale")
    void invoiceNumbers_areUnique() {
        SaleResponse first = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("10.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("10.00"), cashPaymentMethodId))
        ), testUserId);

        SaleResponse second = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("10.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("10.00"), cashPaymentMethodId))
        ), testUserId);

        assertNotEquals(first.sale().invoiceNumber(), second.sale().invoiceNumber());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private static long seedUser(SqliteUserRepository userRepository) {
        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        User user = userRepository.insertUserWithRoles(
                new User(null, "Test Cashier", "cashier-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of(roleId)
        );
        return user.id();
    }

    private static long seedVariantWithStock() {
        long catId = categoryRepository.insertCategory("Cat-" + UUID.randomUUID(), null).id();
        long prodId = productRepository.insertProduct(new Product(
                null, "Product-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "SKU-" + UUID.randomUUID());
        seedInventory(variantId, 50);
        return variantId;
    }

    private static long seedAnotherVariantWithStock(int quantity) {
        long catId = categoryRepository.insertCategory("Cat-" + UUID.randomUUID(), null).id();
        long prodId = productRepository.insertProduct(new Product(
                null, "AnotherProduct-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "SKU2-" + UUID.randomUUID());
        seedInventory(variantId, quantity);
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
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert variant", e);
        }
        throw new IllegalStateException("No variant ID returned");
    }

    private static void seedInventory(long variantId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (variant_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, variantId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("60.00"));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed inventory", e);
        }

        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_adjustments (variant_id, lot_id, quantity_change, reason, adjusted_by, adjusted_at) " +
                "VALUES (?, ?, ?, 'correction', ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, variantId);
            stmt.setLong(2, queryLong("SELECT id FROM inventory_lots WHERE variant_id = ? ORDER BY id DESC LIMIT 1", variantId));
            stmt.setInt(3, quantity);
            stmt.setLong(4, 1);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed inventory adjustment", e);
        }
    }

    private static long seedPaymentMethod() {
        List<com.possum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
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
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("queryLong failed: " + sql, e);
        }
        throw new IllegalStateException("No result: " + sql);
    }

    private static int queryInt(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("queryInt failed", e);
        }
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

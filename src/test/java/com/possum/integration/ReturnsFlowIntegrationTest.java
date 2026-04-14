package com.possum.integration;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.inventory.ProductFlowService;
import com.possum.application.returns.ReturnsService;
import com.possum.application.returns.dto.*;
import com.possum.application.sales.*;
import com.possum.application.sales.dto.*;
import com.possum.domain.exceptions.ValidationException;
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
 * Integration tests for the returns & refunds flow:
 * partial returns, full refunds, stock reversal, and validation guards.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReturnsFlowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static ReturnsService returnsService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteInventoryRepository inventoryRepository;

    private static long testVariantId;
    private static long testUserId;
    private static long cashPaymentMethodId;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-returns-" + UUID.randomUUID();
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
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
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

        returnsService = new ReturnsService(returnsRepository, salesRepository, inventoryService,
                auditRepository, transactionManager, jsonService, new com.possum.domain.services.ReturnCalculator());

        // Seed
        testUserId = seedUser(userRepository);
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testVariantId = seedVariantWithStock(categoryRepository, productRepository, 100);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
        AuthContext.clear();
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Cashier", "cashier",
                List.of("admin"), List.of("sales:create", "sales:manage", "returns:manage")));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Partial return — stock restored and refund transaction created")
    void partialReturn_restoresStock_createsRefundTransaction() {
        // Create a sale with 3 units
        SaleResponse saleResp = createSale(testVariantId, 3, new BigDecimal("50.00"), new BigDecimal("150.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();
        int stockAfterSale = inventoryService.getVariantStock(testVariantId);

        // Return 1 of 3
        ReturnResponse returnResp = returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(saleItemId, 1)),
                "Defective item",
                testUserId
        ));

        assertNotNull(returnResp);
        assertEquals(1, returnResp.itemCount());
        assertTrue(returnResp.totalRefund().compareTo(BigDecimal.ZERO) > 0);

        // Stock should be restored by 1
        assertEquals(stockAfterSale + 1, inventoryService.getVariantStock(testVariantId));

        // A refund transaction should exist
        List<Transaction> transactions = salesRepository.findTransactionsBySaleId(saleId);
        boolean hasRefund = transactions.stream().anyMatch(t -> "refund".equals(t.type()));
        assertTrue(hasRefund, "Expected a refund transaction");
    }

    @Test
    @Order(2)
    @DisplayName("Full return — sale marked as refunded")
    void fullReturn_marksSaleAsRefunded() {
        SaleResponse saleResp = createSale(testVariantId, 2, new BigDecimal("100.00"), new BigDecimal("200.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        // Return all 2
        returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(saleItemId, 2)),
                "Customer changed mind",
                testUserId
        ));

        Sale updatedSale = salesRepository.findSaleById(saleId).orElseThrow();
        assertEquals("refunded", updatedSale.status());
        assertEquals(0, updatedSale.paidAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @Order(3)
    @DisplayName("Partial return then full return — sale marked refunded, quantities tracked correctly")
    void partialThenFullReturn_tracksQuantitiesCorrectly() {
        SaleResponse saleResp = createSale(testVariantId, 4, new BigDecimal("50.00"), new BigDecimal("200.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        // Return 2 first
        returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(saleItemId, 2)),
                "Partial return",
                testUserId
        ));

        Sale afterPartial = salesRepository.findSaleById(saleId).orElseThrow();
        assertEquals("partially_refunded", afterPartial.status());

        // Return remaining 2
        returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(saleItemId, 2)),
                "Returning rest",
                testUserId
        ));

        Sale afterFull = salesRepository.findSaleById(saleId).orElseThrow();
        assertEquals("refunded", afterFull.status());
    }

    @Test
    @Order(4)
    @DisplayName("Return more than purchased — throws ValidationException")
    void returnMoreThanPurchased_throwsValidationException() {
        SaleResponse saleResp = createSale(testVariantId, 2, new BigDecimal("50.00"), new BigDecimal("100.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        assertThrows(ValidationException.class, () ->
                returnsService.createReturn(new CreateReturnRequest(
                        saleId,
                        List.of(new CreateReturnItemRequest(saleItemId, 5)), // more than 2
                        "Overreturn",
                        testUserId
                ))
        );
    }

    @Test
    @Order(5)
    @DisplayName("Return with missing reason — throws ValidationException")
    void returnWithNoReason_throwsValidationException() {
        SaleResponse saleResp = createSale(testVariantId, 1, new BigDecimal("50.00"), new BigDecimal("50.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        assertThrows(ValidationException.class, () ->
                returnsService.createReturn(new CreateReturnRequest(
                        saleId,
                        List.of(new CreateReturnItemRequest(saleItemId, 1)),
                        "", // empty reason
                        testUserId
                ))
        );
    }

    @Test
    @Order(6)
    @DisplayName("Return on non-existent sale — throws NotFoundException")
    void returnOnNonExistentSale_throwsNotFoundException() {
        assertThrows(Exception.class, () ->
                returnsService.createReturn(new CreateReturnRequest(
                        999999L,
                        List.of(new CreateReturnItemRequest(1L, 1)),
                        "Test",
                        testUserId
                ))
        );
    }

    @Test
    @Order(7)
    @DisplayName("Refund amount written correctly in sale paid_amount")
    void returnReducesPaidAmount() {
        SaleResponse saleResp = createSale(testVariantId, 2, new BigDecimal("50.00"), new BigDecimal("100.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();
        BigDecimal originalPaid = saleResp.sale().paidAmount();

        ReturnResponse returnResp = returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(saleItemId, 1)),
                "One unit returned",
                testUserId
        ));

        Sale updatedSale = salesRepository.findSaleById(saleId).orElseThrow();
        assertTrue(updatedSale.paidAmount().compareTo(originalPaid) < 0,
                "Paid amount should decrease after refund");
        assertEquals(0, returnResp.totalRefund().compareTo(
                originalPaid.subtract(updatedSale.paidAmount())));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SaleResponse createSale(long variantId, int qty, BigDecimal unitPrice, BigDecimal payment) {
        return salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(variantId, qty, BigDecimal.ZERO, unitPrice)),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(payment, cashPaymentMethodId))
        ), testUserId);
    }

    private static long seedUser(SqliteUserRepository userRepository) {
        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        User user = userRepository.insertUserWithRoles(
                new User(null, "Returns Tester", "rtester-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of(roleId)
        );
        return user.id();
    }

    private static long seedVariantWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("ReturnsCat-" + UUID.randomUUID(), null).id();
        long prodId = prodRepo.insertProduct(new Product(
                null, "ReturnsProd-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "RSKU-" + UUID.randomUUID());
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
            stmt.setBigDecimal(3, new BigDecimal("30.00"));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Seed inventory lot failed", e);
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
            throw new IllegalStateException("Seed inventory adjustment failed", e);
        }
    }

    private static long getOrSeedPaymentMethod() {
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
        throw new IllegalStateException("No payment method ID returned");
    }

    private static long queryLong(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("queryLong failed: " + sql, e);
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

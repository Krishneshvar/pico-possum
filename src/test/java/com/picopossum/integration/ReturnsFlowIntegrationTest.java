package com.picopossum.integration;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.returns.dto.*;
import com.picopossum.application.sales.*;
import com.picopossum.application.sales.dto.*;
import com.picopossum.domain.exceptions.ValidationException;
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

    private static long testProductId;
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
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore, new com.picopossum.domain.services.StockManager());

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository, productRepository, customerRepository, 
                auditRepository, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager, 
                jsonService, settingsStore, invoiceNumberService);

        returnsService = new ReturnsService(returnsRepository, salesRepository, inventoryService,
                auditRepository, transactionManager, jsonService, new com.picopossum.domain.services.ReturnCalculator());

        // Seed
        testUserId = seedUser(userRepository);
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testProductId = seedProductWithStock(categoryRepository, productRepository, 100);
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
                List.of("admin"), List.of("sales.create", "sales.manage", "returns.manage")));
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
        SaleResponse saleResp = createSale(testProductId, 3, new BigDecimal("50.00"), new BigDecimal("150.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();
        int stockAfterSale = inventoryService.getProductStock(testProductId);

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
        assertEquals(stockAfterSale + 1, inventoryService.getProductStock(testProductId));

        // A refund record should exist within the return
        Return returnRecord = returnsService.getReturn(returnResp.id());
        assertNotNull(returnRecord);
        assertTrue(returnRecord.totalRefund().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @Order(2)
    @DisplayName("Full return — sale marked as refunded")
    void fullReturn_marksSaleAsRefunded() {
        SaleResponse saleResp = createSale(testProductId, 2, new BigDecimal("100.00"), new BigDecimal("200.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

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
    @Order(4)
    @DisplayName("Return more than sold — throws ValidationException")
    void returnMoreThanSold_throwsValidationException() {
        SaleResponse saleResp = createSale(testProductId, 2, new BigDecimal("50.00"), new BigDecimal("100.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        assertThrows(ValidationException.class, () ->
                returnsService.createReturn(new CreateReturnRequest(
                        saleId,
                        List.of(new CreateReturnItemRequest(saleItemId, 5)), 
                        "Overreturn",
                        testUserId
                ))
        );
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SaleResponse createSale(long productId, int qty, BigDecimal unitPrice, BigDecimal payment) {
        return salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(productId, qty, BigDecimal.ZERO, unitPrice)),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(payment, cashPaymentMethodId))
        ), testUserId);
    }

    private static long seedUser(SqliteUserRepository userRepository) {
        User user = userRepository.insertUserWithRoles(
                new User(null, "Returns Tester", "rtester-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of()
        );
        return user.id();
    }

    private static long seedProductWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("ReturnsCat-" + UUID.randomUUID(), null).id();
        Product p = new Product(null, "ReturnsProd-" + UUID.randomUUID(), "desc", catId, null, "RSKU-" + UUID.randomUUID(), new BigDecimal("50.00"), new BigDecimal("30.00"), 10, "active", null, 0, null, null, null);
        long productId = prodRepo.insertProduct(p);
        seedInventory(productId, qty);
        return productId;
    }

    private static void seedInventory(long productId, int quantity) {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO inventory_lots (product_id, quantity, unit_cost, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)")) {
            stmt.setLong(1, productId);
            stmt.setInt(2, quantity);
            stmt.setBigDecimal(3, new BigDecimal("30.00"));
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Seed inventory lot failed", e);
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
            throw new IllegalStateException("Seed inventory adjustment failed", e);
        }
    }

    private static long getOrSeedPaymentMethod() {
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
        throw new IllegalStateException("No payment method ID returned");
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

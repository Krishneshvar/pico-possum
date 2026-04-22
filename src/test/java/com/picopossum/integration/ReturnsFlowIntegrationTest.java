package com.picopossum.integration;

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
import com.picopossum.application.audit.AuditService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReturnsFlowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static ReturnsService returnsService;
    private static InventoryService inventoryService;
    private static SqliteSalesRepository salesRepository;
    private static SqliteReturnsRepository returnsRepository;
    private static AuditService auditService;

    private static long testProductId;
    private static long testTaxProductId;
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
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        auditService = new AuditService(auditRepository, jsonService.getObjectMapper());

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditService,
                transactionManager, jsonService, settingsStore, null);

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository, productRepository, customerRepository, 
                auditService, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager, 
                jsonService, settingsStore, invoiceNumberService, returnsRepository);

        returnsService = new ReturnsService(returnsRepository, salesRepository, inventoryService,
                auditService, transactionManager, jsonService, new com.picopossum.domain.services.ReturnCalculator(), invoiceNumberService);

        // Seed
        cashPaymentMethodId = getOrSeedPaymentMethod();
        testProductId = seedProductWithStock(categoryRepository, productRepository, 100, BigDecimal.ZERO);
        testTaxProductId = seedProductWithStock(categoryRepository, productRepository, 100, new BigDecimal("5.00"));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (auditService != null) {
            auditService.waitForCompletion();
            auditService.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Help Windows release file handles
        System.gc();
        System.runFinalization();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        if (appPaths != null && Files.exists(appPaths.getAppRoot())) {
            deleteDirectory(appPaths.getAppRoot());
        }
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
                "Defective item"
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
        
        // Verify denormalized fields
        List<ReturnItem> items = returnsRepository.findReturnItems(returnResp.id());
        assertEquals(1, items.size());
        assertEquals(testProductId, items.get(0).productId());
        assertNotNull(items.get(0).sku());
        assertNotNull(items.get(0).productName());
    }

    @Test
    @Order(4)
    @DisplayName("Complex return — items with tax and global discount")
    void returnWithTaxAndDiscount_calculatesProRatedRefund() {
        // Item 1: MRP 50, Tax 5% ($2.50). Total w/ Tax = 52.50.
        // Item 2: MRP 50, Tax 5% ($2.50). Total w/ Tax = 52.50.
        // Bill Total w/ Tax = 105.00.
        // Global Discount = $5.00.
        // Paid = $100.00.
        
        // Refund for 1 item should be exactly $50.00.
        
        SaleResponse saleResp = salesService.createSale(new CreateSaleRequest(
                List.of(
                    new CreateSaleItemRequest(testTaxProductId, 1, BigDecimal.ZERO, new BigDecimal("50.00")),
                    new CreateSaleItemRequest(testTaxProductId, 1, BigDecimal.ZERO, new BigDecimal("50.00"))
                ),
                null, new BigDecimal("5.00"),
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
        ));
        
        long saleId = saleResp.sale().id();
        long item1Id = saleResp.items().get(0).id();
        
        ReturnResponse returnResp = returnsService.createReturn(new CreateReturnRequest(
                saleId,
                List.of(new CreateReturnItemRequest(item1Id, 1)),
                "Tax/Discount Test"
        ));
        
        assertEquals(0, returnResp.totalRefund().compareTo(new BigDecimal("50.00")));
    }

    @Test
    @Order(5)
    @DisplayName("Search and Filter returns")
    void filterReturns_returnsCorrectResults() {
        // Clear or just use existing ones
        ReturnFilter filter = new ReturnFilter(null, null, null, null, null, null, "Defective", 1, 10, "created_at", "DESC");
        PagedResult<Return> result = returnsService.getReturns(filter);
        
        // Should have at least one from test 1
        assertTrue(result.totalCount() >= 1);
        assertTrue(result.items().stream().anyMatch(r -> r.reason().contains("Defective")));
        
        // Filter by amount
        ReturnFilter amountFilter = new ReturnFilter(null, null, null, new BigDecimal("10.00"), new BigDecimal("100.00"), null, null, 1, 10, "total_refund", "ASC");
        PagedResult<Return> amountResult = returnsService.getReturns(amountFilter);
        assertNotNull(amountResult);
        for (Return r : amountResult.items()) {
            assertTrue(r.totalRefund().compareTo(new BigDecimal("10.00")) >= 0);
            assertTrue(r.totalRefund().compareTo(new BigDecimal("100.00")) <= 0);
        }
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
                "Customer changed mind"
        ));

        Sale updatedSale = salesRepository.findSaleById(saleId).orElseThrow();
        assertEquals("refunded", updatedSale.status());
        assertEquals(0, updatedSale.paidAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @Order(3)
    @DisplayName("Return more than sold — throws ValidationException")
    void returnMoreThanSold_throwsValidationException() {
        SaleResponse saleResp = createSale(testProductId, 2, new BigDecimal("50.00"), new BigDecimal("100.00"));
        long saleId = saleResp.sale().id();
        long saleItemId = saleResp.items().get(0).id();

        assertThrows(ValidationException.class, () ->
                returnsService.createReturn(new CreateReturnRequest(
                        saleId,
                        List.of(new CreateReturnItemRequest(saleItemId, 5)), 
                        "Overreturn"
                ))
        );
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SaleResponse createSale(long productId, int qty, BigDecimal unitPrice, BigDecimal payment) {
        return salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(productId, qty, BigDecimal.ZERO, unitPrice)),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(payment, cashPaymentMethodId))
        ));
    }

    private static long seedProductWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty, BigDecimal taxRate) {
        long catId = catRepo.insertCategory("ReturnsCat-" + UUID.randomUUID(), null).id();
        Product p = new Product(null, "ReturnsProd-" + UUID.randomUUID(), "desc", catId, null, taxRate, "RSKU-" + UUID.randomUUID(), null, new BigDecimal("50.00"), new BigDecimal("30.00"), 10, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 0, null, null, null);
        long productId = prodRepo.insertProduct(p);
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
        } catch (SQLException e) {
            throw new IllegalStateException("Seed inventory target failed", e);
        }
    }

    private static long getOrSeedPaymentMethod() {
        try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(
                "INSERT INTO payment_methods (name, code) VALUES ('Cash', 'CA') ON CONFLICT DO NOTHING")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Might exist
        }

        List<com.picopossum.domain.model.PaymentMethod> methods = salesRepository.findPaymentMethods();
        for(var m : methods) if("Cash".equals(m.name())) return m.id();
        throw new IllegalStateException("Failed to seed payment method");
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        
        // Retry logic for Windows file locks
        for (int i = 0; i < 3; i++) {
            try (var walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        // Ignore and retry
                    }
                });
            }
            if (!Files.exists(root)) return;
            System.gc();
            try { Thread.sleep(100 * (i + 1)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}

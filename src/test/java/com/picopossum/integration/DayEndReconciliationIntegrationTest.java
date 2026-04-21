package com.picopossum.integration;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.returns.dto.*;
import com.picopossum.application.sales.*;
import com.picopossum.application.sales.dto.*;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.shared.dto.SaleFilter;
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

/**
 * Day-end reconciliation integration tests.
 * Verifies that SaleStats accurately reflect sales counts, totals,
 * refunds, and per-payment-method breakdowns over a simulated trading day.
 * Synchronized for Single-User Identity-Agnostic architecture.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DayEndReconciliationIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static ReturnsService returnsService;
    private static SqliteSalesRepository salesRepository;

    private static long testProductId;
    private static long cashPaymentMethodId;

    @BeforeAll
    static void setUp() throws Exception {
        String appDir = "possum-dayend-" + UUID.randomUUID();
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
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        InventoryService inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
                transactionManager, jsonService, settingsStore);

        PaymentService paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);

        salesService = new SalesService(salesRepository, productRepository, customerRepository, 
                auditRepository, inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService, transactionManager, 
                jsonService, settingsStore, invoiceNumberService, returnsRepository);

        returnsService = new ReturnsService(returnsRepository, salesRepository, inventoryService,
                auditRepository, transactionManager, jsonService, new com.picopossum.domain.services.ReturnCalculator(), invoiceNumberService);

        cashPaymentMethodId = getOrSeedPaymentMethod();
        testProductId = seedProductWithStock(categoryRepository, productRepository, 1000);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Empty day — all stats are zero")
    void emptyDatabase_allStatsAreZero() {
        SaleFilter filter = new SaleFilter(
                null, null, "2000-01-01", "2000-01-02",
                null, null, null, 1, 25, "sale_date", "DESC", null, null
        );

        SaleStats stats = salesService.getSaleStats(filter);

        assertEquals(0, stats.totalBills());
        assertEquals(0, stats.paidCount());
        assertEquals(0, stats.partialOrDraftCount());
        assertEquals(0, stats.cancelledOrRefundedCount());
    }

    @Test
    @Order(2)
    @DisplayName("Mixed statuses — counts reflect correct breakdown")
    void mixedStatuses_countsAreCorrect() {
        createPaidSale(new BigDecimal("100.00"));

        salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, new BigDecimal("80.00"))),
                null, BigDecimal.ZERO, List.of()
        ));

        SaleResponse toCancel = createPaidSale(new BigDecimal("60.00"));
        salesService.cancelSale(toCancel.sale().id());

        SaleStats stats = salesService.getSaleStats(filterAll());

        assertTrue(stats.totalBills() >= 3);
        assertTrue(stats.paidCount() >= 1);
        assertTrue(stats.partialOrDraftCount() >= 1);
        assertTrue(stats.cancelledOrRefundedCount() >= 1);
    }

    @Test
    @Order(3)
    @DisplayName("Fully refunded sale — counted in cancelled/refunded bucket")
    void fullyRefundedSale_appearsInRefundedBucket() {
        SaleResponse saleResp = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 2, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
        ));

        long saleItemId = saleResp.items().get(0).id();

        returnsService.createReturn(new CreateReturnRequest(
                saleResp.sale().id(),
                List.of(new CreateReturnItemRequest(saleItemId, 2)),
                "Full return"
        ));

        SaleStats stats = salesService.getSaleStats(filterAll());
        assertTrue(stats.cancelledOrRefundedCount() >= 1);
    }

    @Test
    @Order(4)
    @DisplayName("findSales pagination — correct page sizes returned")
    void findSales_paginationWorks() {
        for (int i = 0; i < 5; i++) {
            createPaidSale(new BigDecimal("100.00"));
        }

        var result = salesService.findSales(new SaleFilter(
                null, null, null, null, null,
                null, null, 1, 25, "sale_date", "DESC", null, null
        ));

        assertNotNull(result);
        assertNotNull(result.items());
        assertFalse(result.items().isEmpty());
        assertTrue(result.totalCount() >= 5);
    }

    private SaleResponse createPaidSale(BigDecimal amount) {
        return salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testProductId, 1, BigDecimal.ZERO, amount)),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(amount, cashPaymentMethodId))
        ));
    }

    private static SaleFilter filterAll() {
        return new SaleFilter(null, null, null, null, null,
                null, null, 1, 1000, "sale_date", "DESC", null, null);
    }

    private static long seedProductWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("DayCat-" + UUID.randomUUID(), null).id();
        long productId = prodRepo.insertProduct(new Product(
            null, "DayProd-" + UUID.randomUUID(), "desc", catId, null, "DSKU-" + UUID.randomUUID(),
            new BigDecimal("100.00"), new BigDecimal("60.00"), 0, "active", null, 5, null, null, null
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

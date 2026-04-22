package com.picopossum.integration;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.reports.dto.*;
import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.returns.dto.CreateReturnItemRequest;
import com.picopossum.application.returns.dto.CreateReturnRequest;
import com.picopossum.application.sales.InvoiceNumberService;
import com.picopossum.application.sales.PaymentService;
import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.dto.*;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.application.audit.AuditService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.shared.util.TimeUtil;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InsightsFlowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static ReturnsService returnsService;
    private static ReportsService reportsService;
    private static InventoryService inventoryService;
    private static SqliteProductRepository productRepository;
    private static SqliteCategoryRepository categoryRepository;

    private static long productId1;
    private static long productId2;
    private static long cashId;
    private static long upiId;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-insights-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        JsonService jsonService = new JsonService();
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);

        categoryRepository = new SqliteCategoryRepository(databaseManager);
        productRepository = new SqliteProductRepository(databaseManager);
        SqliteSalesRepository salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);
        SqliteReportsRepository reportsRepository = new SqliteReportsRepository(databaseManager);

        AuditService auditService = new AuditService(auditRepository, jsonService.getObjectMapper());
        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditService,
                transactionManager, jsonService, settingsStore, null);
        
        InvoiceNumberService invService = new InvoiceNumberService(salesRepository);
        reportsService = new ReportsService(reportsRepository, productFlowRepository);
        returnsService = new ReturnsService(returnsRepository, salesRepository, inventoryService, auditService,
                transactionManager, jsonService, new com.picopossum.domain.services.ReturnCalculator(), invService);
        
        PaymentService paymentService = new PaymentService(salesRepository);
        salesService = new SalesService(salesRepository, productRepository, customerRepository, auditService,
                inventoryService, new com.picopossum.domain.services.SaleCalculator(), paymentService,
                transactionManager, jsonService, settingsStore, invService, returnsRepository);

        // Seed data
        cashId = salesRepository.findPaymentMethods().stream().filter(pm -> pm.name().equalsIgnoreCase("Cash")).findFirst().get().id();
        upiId = salesRepository.findPaymentMethods().stream().filter(pm -> pm.name().equalsIgnoreCase("UPI")).findFirst().get().id();

        productId1 = seedProduct("Phone", new BigDecimal("1000.00"), new BigDecimal("700.00"), new BigDecimal("10.00")); // 10% tax
        productId2 = seedProduct("Case", new BigDecimal("50.00"), new BigDecimal("20.00"), BigDecimal.ZERO); // No tax
        
        inventoryService.receiveInventory(productId1, 10, "Initial stock");
        inventoryService.receiveInventory(productId2, 100, "Initial stock");
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) {}
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Comprehensive Sales and Insights Flow")
    void fullInsightsFlow() {
        LocalDate today = LocalDate.now();
        
        // Sale 1: 1 Phone (1000 + 100 tax = 1100). Paid 1100 Cash.
        salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(productId1, 1, BigDecimal.ZERO, new BigDecimal("1000.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("1100.00"), cashId))
        ));

        // Sale 2: 2 Cases (100 total). 10% Overall Discount = 10. Paid 90 UPI.
        SaleResponse sale2 = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(productId2, 2, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, new BigDecimal("10.00"),
                List.of(new PaymentRequest(new BigDecimal("90.00"), upiId))
        ));

        // Return 1 Case from Sale 2. Refund 45.
        long sale2Id = sale2.sale().id();
        long sale2ItemId = sale2.items().get(0).id();
        returnsService.createReturn(new CreateReturnRequest(sale2Id, List.of(new CreateReturnItemRequest(sale2ItemId, 1)), "Damaged"));

        // Manual Adjustment: Deduct 1 Phone due to "damage"
        inventoryService.adjustInventory(productId1, -1, InventoryReason.DAMAGE, "manual", null, "Broken screen");

        // --- VERIFICATIONS ---

        // 1. Sales Summary
        SalesReportSummary summary = reportsService.getSalesSummary(today, today, null);
        
        // Total Sales (header amount): 1100 (Sale 1) + 90 (Sale 2) = 1190.
        assertEquals(0, new BigDecimal("1190.00").compareTo(summary.totalSales()), "Total Sales mismatch");
        
        // Total Refunds: 45.00
        assertEquals(0, new BigDecimal("45.00").compareTo(summary.totalRefunds()), "Total Refunds mismatch");
        
        // Total Tax: 100.00 (from Sale 1)
        assertEquals(0, new BigDecimal("100.00").compareTo(summary.totalTax()), "Total Tax mismatch");
        
        // Total Cost: 
        // Sale 1: 1 Phone @ 700 = 700.
        // Sale 2: 2 Cases @ 20 = 40.
        // Total = 740.
        assertEquals(0, new BigDecimal("740.00").compareTo(summary.totalCost()), "Total Cost mismatch");

        // Net Sales = 1190 (Total) - 45 (Refund) - 100 (Tax) = 1045.
        assertEquals(0, new BigDecimal("1045.00").compareTo(summary.netSales()), "Net Sales mismatch");

        // Gross Profit = 1045 (Net Sales) - 740 (Cost) = 305.
        assertEquals(0, new BigDecimal("305.00").compareTo(summary.grossProfit()), "Gross Profit mismatch");

        // 2. Daily Breakdown
        DailyReport daily = reportsService.getSalesAnalytics(today, today, null);
        assertEquals(1, daily.breakdown().size());
        assertEquals(2, daily.breakdown().get(0).totalTransactions());

        // 3. Top Products
        List<TopProduct> top = reportsService.getTopProducts(today, today, 10, null);
        assertTrue(top.stream().anyMatch(p -> p.productName().equals("Phone") && p.totalQuantitySold() == 1));
        assertTrue(top.stream().anyMatch(p -> p.productName().equals("Case") && p.totalQuantitySold() == 2));

        // 4. Product Flow Report
        ProductFlowReport flow = reportsService.getProductFlowReport(productId1, 10, 0, null, null, null);
        // Movements: 
        // +10 (Receive)
        // -1 (Sale)
        // -1 (Adjustment/Damage)
        assertEquals(3, flow.flows().size());
        
        // 5. Business Health
        BusinessHealthReport health = reportsService.getBusinessHealthOverview(today, today);
        assertEquals(0, summary.totalSales().compareTo(health.salesSummary().totalSales()));
        // Product 1 stock: 10 - 1 - 1 = 8. (Alert cap 10 -> Low Stock)
        assertTrue(health.lowStockCount() >= 1);
    }

    @Test
    @Order(2)
    @DisplayName("Advanced Reporting and Analytics")
    void advancedReportingFlow() {
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);
        
        // Sale last month
        salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(productId2, 5, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("250.00"), cashId))
        ));

        // 1. Monthly Report
        MonthlyReport monthly = reportsService.getMonthlyReport(lastMonth, today, null);
        // Expect at least 2 months if today is different month than lastMonth, or 1 if same.
        assertTrue(monthly.breakdown().size() >= 1);
        assertTrue(monthly.breakdown().stream().anyMatch(b -> b.totalSales().compareTo(BigDecimal.ZERO) > 0));

        // 2. Payment Method Stats
        List<PaymentMethodStat> pmStats = reportsService.getSalesByPaymentMethod(lastMonth, today);
        assertTrue(pmStats.stream().anyMatch(s -> s.paymentMethod().equalsIgnoreCase("Cash") && s.totalAmount().compareTo(BigDecimal.ZERO) > 0));
        assertTrue(pmStats.stream().anyMatch(s -> s.paymentMethod().equalsIgnoreCase("UPI") && s.totalAmount().compareTo(BigDecimal.ZERO) > 0));

        // 3. Filtered Product Flow
        // Filter by 'sale' only
        ProductFlowReport flowSaleOnly = reportsService.getProductFlowReport(productId1, 50, 0, null, null, List.of("sale"));
        assertTrue(flowSaleOnly.flows().stream().allMatch(f -> f.eventType().equalsIgnoreCase("sale")));
        
        // 4. Comparison Report
        ComparisonReport comparison = reportsService.getSalesComparison(today, today, lastMonth, lastMonth);
        assertNotNull(comparison);
        assertTrue(comparison.periodLabel().contains(lastMonth.getYear() + "")); 
    }

    private static long seedProduct(String name, BigDecimal mrp, BigDecimal cost, BigDecimal tax) {
        long catId = categoryRepository.insertCategory("TestCat-" + UUID.randomUUID(), null).id();
        Product p = new Product(null, name, "desc", catId, null, tax, "SKU-" + UUID.randomUUID(), null, mrp, cost, 10, com.picopossum.domain.model.ProductStatus.ACTIVE, null, 0, null, null, null);
        return productRepository.insertProduct(p);
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {}
            });
        }
    }
}

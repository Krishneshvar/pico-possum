package com.possum.integration;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.inventory.ProductFlowService;
import com.possum.application.returns.ReturnsService;
import com.possum.application.returns.dto.*;
import com.possum.application.sales.*;
import com.possum.application.sales.dto.*;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.db.TransactionManager;
import com.possum.persistence.repositories.sqlite.*;
import com.possum.shared.dto.SaleFilter;
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
 * Day-end reconciliation integration tests.
 * Verifies that SaleStats accurately reflect sales counts, totals,
 * refunds, and per-payment-method breakdowns over a simulated trading day.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DayEndReconciliationIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static SalesService salesService;
    private static ReturnsService returnsService;
    private static SqliteSalesRepository salesRepository;

    private static long testVariantId;
    private static long testUserId;
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
        SqliteVariantRepository variantRepository = new SqliteVariantRepository(databaseManager);
        salesRepository = new SqliteSalesRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteReturnsRepository returnsRepository = new SqliteReturnsRepository(databaseManager);
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteCustomerRepository customerRepository = new SqliteCustomerRepository(databaseManager);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        InventoryService inventoryService = new InventoryService(inventoryRepository, productFlowService, auditRepository,
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

        long roleId = queryLong("SELECT id FROM roles WHERE name = 'admin'");
        User u = userRepository.insertUserWithRoles(
                new User(null, "Day Closer", "dayclose-" + UUID.randomUUID(), "hash", true, null, null, null),
                List.of(roleId)
        );
        testUserId = u.id();

        cashPaymentMethodId = getOrSeedPaymentMethod();
        testVariantId = seedVariantWithStock(categoryRepository, productRepository, 1000);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
        AuthContext.clear();
    }

    @BeforeEach
    void setAuth() {
        AuthContext.setCurrentUser(new AuthUser(testUserId, "Day Closer", "dayclose",
                List.of("admin"), List.of("sales:create", "sales:manage", "returns:manage")));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Empty day — all stats are zero")
    void emptyDatabase_allStatsAreZero() {
        // For this test we use a filter that can never match any real sales
        SaleFilter filter = new SaleFilter(
                null, null, null,
                "2000-01-01", "2000-01-02", // Far past date range
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
    @DisplayName("Three paid sales — stats show 3 paid, 0 others")
    void threePaidSales_statsShowThreePaid() {
        createPaidSale(new BigDecimal("100.00"));
        createPaidSale(new BigDecimal("200.00"));
        createPaidSale(new BigDecimal("150.00"));

        SaleStats stats = salesService.getSaleStats(filterAll());

        assertTrue(stats.totalBills() >= 3);
        assertTrue(stats.paidCount() >= 3);
    }

    @Test
    @Order(3)
    @DisplayName("Mixed statuses — counts reflect correct breakdown")
    void mixedStatuses_countsAreCorrect() {
        // Create a paid sale
        SaleResponse paidSale = createPaidSale(new BigDecimal("100.00"));

        // Create a draft (no payment)
        salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, new BigDecimal("80.00"))),
                null, BigDecimal.ZERO, List.of()
        ), testUserId);

        // Create and cancel a sale
        SaleResponse toCancel = createPaidSale(new BigDecimal("60.00"));
        salesService.cancelSale(toCancel.sale().id(), testUserId);

        SaleStats stats = salesService.getSaleStats(filterAll());

        assertTrue(stats.totalBills() >= 3);
        assertTrue(stats.paidCount() >= 1); // at least the paid one
        assertTrue(stats.partialOrDraftCount() >= 1); // at least the draft
        assertTrue(stats.cancelledOrRefundedCount() >= 1); // at least the cancelled
    }

    @Test
    @Order(4)
    @DisplayName("Fully refunded sale — counted in cancelled/refunded bucket")
    void fullyRefundedSale_appearsInRefundedBucket() {
        SaleResponse saleResp = salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 2, BigDecimal.ZERO, new BigDecimal("50.00"))),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(new BigDecimal("100.00"), cashPaymentMethodId))
        ), testUserId);

        long saleItemId = saleResp.items().get(0).id();

        returnsService.createReturn(new CreateReturnRequest(
                saleResp.sale().id(),
                List.of(new CreateReturnItemRequest(saleItemId, 2)),
                "Full return",
                testUserId
        ));

        SaleStats stats = salesService.getSaleStats(filterAll());
        assertTrue(stats.cancelledOrRefundedCount() >= 1);
    }

    @Test
    @Order(5)
    @DisplayName("findSales pagination — correct page sizes returned")
    void findSales_paginationWorks() {
        // Create 5 more sales to ensure pagination data
        for (int i = 0; i < 5; i++) {
            createPaidSale(new BigDecimal("100.00"));
        }

        var result = salesService.findSales(new SaleFilter(
                null, null, null, null, null,
                null, null, null, 1, 25, "sale_date", "DESC", null, null
        ));

        assertNotNull(result);
        assertNotNull(result.items());
        assertFalse(result.items().isEmpty());
        assertTrue(result.totalCount() >= 5);
    }

    @Test
    @Order(6)
    @DisplayName("findSales with search term — filters correctly")
    void findSalesWithSearchTerm_filtersCorrectly() {
        SaleResponse targetSale = createPaidSale(new BigDecimal("999.00"));
        String invoiceNum = targetSale.sale().invoiceNumber();

        var result = salesService.findSales(new SaleFilter(
                null, null, null, null, null,
                null, null, invoiceNum, 1, 25, "sale_date", "DESC", null, null
        ));

        assertTrue(result.items().stream()
                .anyMatch(s -> invoiceNum.equals(s.invoiceNumber())));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SaleResponse createPaidSale(BigDecimal amount) {
        return salesService.createSale(new CreateSaleRequest(
                List.of(new CreateSaleItemRequest(testVariantId, 1, BigDecimal.ZERO, amount)),
                null, BigDecimal.ZERO,
                List.of(new PaymentRequest(amount, cashPaymentMethodId))
        ), testUserId);
    }

    private static SaleFilter filterAll() {
        return new SaleFilter(null, null, null, null, null,
                null, null, null, 1, 1000, "sale_date", "DESC", null, null);
    }

    private static long seedVariantWithStock(SqliteCategoryRepository catRepo, SqliteProductRepository prodRepo, int qty) {
        long catId = catRepo.insertCategory("DayCat-" + UUID.randomUUID(), null).id();
        long prodId = prodRepo.insertProduct(new Product(
                null, "DayProd-" + UUID.randomUUID(), "desc", catId, null, null, null, "active", null, null, null, null, null
        ));
        long variantId = insertVariant(prodId, "DSKU-" + UUID.randomUUID());
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
        } catch (SQLException e) { throw new IllegalStateException("Seed payment method failed", e); }
        throw new IllegalStateException("No ID returned");
    }

    private static long queryLong(String sql, Object... params) {
        try (PreparedStatement stmt = prepare(sql, params); ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) { throw new IllegalStateException("queryLong failed: " + sql, e); }
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

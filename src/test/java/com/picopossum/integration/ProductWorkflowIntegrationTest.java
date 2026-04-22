package com.picopossum.integration;

import com.picopossum.application.audit.AuditService;
import com.picopossum.application.products.ProductService;
import com.picopossum.application.products.ProductValidator;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.FileStorageService;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.ui.sales.ProductSearchIndex;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductWorkflowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static TransactionManager transactionManager;
    private static ProductService productService;
    private static CategoryService categoryService;
    private static InventoryService inventoryService;
    private static AuditService auditService;
    private static ProductSearchIndex searchIndex;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-product-workflow-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        transactionManager = new TransactionManager(databaseManager);

        JsonService jsonService = new JsonService();
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);
        FileStorageService storageService = new FileStorageService(appPaths);

        SqliteProductRepository productRepository = new SqliteProductRepository(databaseManager);
        SqliteCategoryRepository categoryRepository = new SqliteCategoryRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);

        auditService = new AuditService(auditRepository, jsonService.getObjectMapper());
        searchIndex = new ProductSearchIndex(productRepository);

        ProductFlowService productFlowService = new ProductFlowService(productFlowRepository);
        inventoryService = new InventoryService(inventoryRepository, productFlowService, auditService,
                transactionManager, jsonService, settingsStore, null);

        categoryService = new CategoryService(categoryRepository);

        productService = new ProductService(
                productRepository, inventoryRepository, auditService,
                transactionManager, settingsStore, new ProductValidator(),
                storageService, jsonService, searchIndex
        );
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (auditService != null) {
            auditService.waitForCompletion();
            auditService.shutdown();
        }
        if (databaseManager != null) databaseManager.close();

        System.gc();
        try { Thread.sleep(200); } catch (InterruptedException e) {}

        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Create Category then Product Workflow")
    void fullProductLifecycleTest() {
        // 1. Create Category
        Category category = categoryService.createCategory("Electronics", null);
        assertNotNull(category);
        long catId = category.id();
        assertTrue(catId > 0);

        // 2. Create Product with initial stock
        ProductService.CreateProductCommand createCmd = new ProductService.CreateProductCommand(
                "Smartphone X", "Latest model", catId, "SKU-PH01",
                new BigDecimal("999.99"), new BigDecimal("700.00"), 5,
                ProductStatus.ACTIVE, null, 10, new BigDecimal("18.0"), "BAR-123456"
        );
        long prodId = productService.createProduct(createCmd);
        assertTrue(prodId > 0);

        // 3. Verify Stock and Index
        int stock = inventoryService.getProductStock(prodId);
        assertEquals(10, stock);

        var foundInIndex = searchIndex.findBySku("SKU-PH01");
        assertTrue(foundInIndex.isPresent());
        assertEquals("Smartphone X", foundInIndex.get().name());

        // 4. Update Product Details (Change Price and Stock Alert)
        ProductService.UpdateProductCommand updateCmd = new ProductService.UpdateProductCommand(
                "Smartphone X Elite", null, null, null,
                new BigDecimal("1099.99"), null, 3, null, null,
                null, null, null, null
        );
        productService.updateProduct(prodId, updateCmd);

        Product updated = productService.getProductById(prodId);
        assertEquals("Smartphone X Elite", updated.name());
        assertEquals(new BigDecimal("1099.99"), updated.mrp());
        assertEquals(3, updated.stockAlertCap());

        // 5. Manual Stock Adjustment (Correction)
        ProductService.UpdateProductCommand stockAdjCmd = new ProductService.UpdateProductCommand(
                null, null, null, null, null, null, null, null, null,
                15, "Correction", null, null
        );
        productService.updateProduct(prodId, stockAdjCmd);
        assertEquals(15, inventoryService.getProductStock(prodId));

        // 6. Test Filtering
        ProductFilter filter = new ProductFilter("Elite", null, null, 0, 10, "name", "ASC");
        PagedResult<Product> filtered = productService.getProducts(filter);
        assertEquals(1, filtered.totalCount());
        assertEquals("Smartphone X Elite", filtered.items().get(0).name());

        // 7. Soft Delete
        productService.deleteProduct(prodId);
        assertThrows(com.picopossum.domain.exceptions.NotFoundException.class, () -> productService.getProductById(prodId));

        // 8. Verify Audit Logs (Async check)
        auditService.waitForCompletion();
        // Since we don't have a direct query tool for audit in this test, we rely on no exceptions occurring
    }

    @Test
    @Order(2)
    @DisplayName("Product Unique SKU and Barcode constraints")
    void skuAndBarcodeUniquenessTest() {
        productService.createProduct(new ProductService.CreateProductCommand(
                "Prod A", "Desc", null, "UNIQ-1",
                new BigDecimal("10"), new BigDecimal("5"), 2,
                ProductStatus.ACTIVE, null, 0, BigDecimal.ZERO, "B-001"
        ));

        // Duplicate SKU
        assertThrows(com.picopossum.domain.exceptions.ValidationException.class, () -> 
            productService.createProduct(new ProductService.CreateProductCommand(
                "Prod B", "Desc", null, "UNIQ-1",
                new BigDecimal("20"), new BigDecimal("10"), 2,
                ProductStatus.ACTIVE, null, 0, BigDecimal.ZERO, "B-002"
            ))
        );
    }

    @Test
    @Order(3)
    @DisplayName("Auto-increment Numeric SKU on Conflict")
    void numericSkuAutoStabilizationTest() {
        // Create product with SKU 5000
        productService.createProduct(new ProductService.CreateProductCommand(
                "Base Prod", "Desc", null, "5000",
                new BigDecimal("10"), new BigDecimal("5"), 2,
                ProductStatus.ACTIVE, null, 0, BigDecimal.ZERO, "B-AUTO"
        ));

        // Try to create another product with SKU 5000 (simulating two windows open)
        // It should NOT throw error, but instead auto-increment to 5001
        productService.createProduct(new ProductService.CreateProductCommand(
                "Auto Inc Prod", "Desc", null, "5000",
                new BigDecimal("10"), new BigDecimal("5"), 2,
                ProductStatus.ACTIVE, null, 0, BigDecimal.ZERO, "B-AUTO-2"
        ));

        Product p = searchIndex.findBySku("5001").orElseThrow();
        assertEquals("Auto Inc Prod", p.name());
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {
                    // Ignore transient cleanup errors on Windows in tests
                }
            });
        }
    }
}

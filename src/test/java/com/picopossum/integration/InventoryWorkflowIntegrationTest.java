package com.picopossum.integration;

import com.picopossum.application.audit.AuditService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.products.ProductService;
import com.picopossum.application.products.ProductValidator;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.model.Product;
import com.picopossum.domain.model.ProductStatus;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.FileStorageService;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryWorkflowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static InventoryService inventoryService;
    private static ProductService productService;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-inventory-workflow-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();

        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductRepository productRepository = new SqliteProductRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        SqliteProductFlowRepository flowRepository = new SqliteProductFlowRepository(databaseManager);
        
        JsonService jsonService = new JsonService();
        TransactionManager transactionManager = new TransactionManager(databaseManager);
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);
        FileStorageService storageService = new FileStorageService(appPaths);
        AuditService auditService = new AuditService(auditRepository, jsonService.getObjectMapper());

        ProductFlowService productFlowService = new ProductFlowService(flowRepository);
        inventoryService = new InventoryService(
                inventoryRepository, productFlowService, auditService,
                transactionManager, jsonService, settingsStore, null // No UI search index in headless test
        );
        productService = new ProductService(
                productRepository, inventoryRepository, auditService,
                transactionManager, settingsStore, new ProductValidator(),
                storageService, jsonService, null
        );
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) databaseManager.close();
        System.gc();
        try { Thread.sleep(200); } catch (InterruptedException e) {}
        if (appPaths != null) deleteDirectory(appPaths.getAppRoot());
    }

    @Test
    @Order(1)
    @DisplayName("Stock Lifecycle: Receive -> Adjust -> Deduct -> Alert")
    void inventoryLifecycleTest() {
        // 1. Create Product
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "Test Prod", "Desc", null, "INV-T1",
                new BigDecimal("100"), new BigDecimal("50"), 10,
                ProductStatus.ACTIVE, null, 0, BigDecimal.ZERO, "B-001"
        );
        long productId = productService.createProduct(cmd);
        
        assertEquals(0, inventoryService.getProductStock(productId));

        // 2. Receive Inventory
        inventoryService.receiveInventory(productId, 50, "Initial load");
        assertEquals(50, inventoryService.getProductStock(productId));

        // 3. Adjust Inventory (Add more)
        inventoryService.adjustInventory(productId, 20, InventoryReason.CORRECTION, "manual", null, "Found more");
        assertEquals(70, inventoryService.getProductStock(productId));

        // 4. Deduct Inventory (Sale simulated)
        inventoryService.deductStock(productId, 65, InventoryReason.SALE, "sale", 123L);
        assertEquals(5, inventoryService.getProductStock(productId));

        // 5. Check Low Stock Alert
        List<Product> lowStock = inventoryService.getLowStockAlerts();
        assertTrue(lowStock.stream().anyMatch(p -> p.id().equals(productId)));
    }

    @Test
    @Order(2)
    @DisplayName("Inventory Restrictions: Block Negative Stock")
    void inventoryRestrictionsTest() {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "Restricted Prod", "Desc", null, "INV-R1",
                new BigDecimal("100"), new BigDecimal("50"), 10,
                ProductStatus.ACTIVE, null, 5, BigDecimal.ZERO, "B-002"
        );
        long productId = productService.createProduct(cmd);

        // Try to deduct 10 (stock is only 5)
        assertThrows(com.picopossum.domain.exceptions.InsufficientStockException.class, () -> 
            inventoryService.deductStock(productId, 10, InventoryReason.SALE, "sale", 999L));
        
        // Stock should still be 5
        assertEquals(5, inventoryService.getProductStock(productId));
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) {}
            });
        }
    }
}

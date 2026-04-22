package com.picopossum.integration;

import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.products.ProductService;
import com.picopossum.application.products.ProductValidator;
import com.picopossum.domain.model.Category;
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
class CategoryWorkflowIntegrationTest {

    private static AppPaths appPaths;
    private static DatabaseManager databaseManager;
    private static CategoryService categoryService;
    private static ProductService productService;

    @BeforeAll
    static void setUp() {
        String appDir = "possum-category-workflow-" + UUID.randomUUID();
        appPaths = new AppPaths(appDir);
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();

        SqliteCategoryRepository categoryRepository = new SqliteCategoryRepository(databaseManager);
        SqliteProductRepository productRepository = new SqliteProductRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteAuditRepository auditRepository = new SqliteAuditRepository(databaseManager);
        
        JsonService jsonService = new JsonService();
        TransactionManager transactionManager = new TransactionManager(databaseManager);
        SettingsStore settingsStore = new SettingsStore(appPaths, jsonService);
        FileStorageService storageService = new FileStorageService(appPaths);
        com.picopossum.application.audit.AuditService auditService = new com.picopossum.application.audit.AuditService(auditRepository, jsonService.getObjectMapper());

        categoryService = new CategoryService(categoryRepository);
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
    @DisplayName("Category Tree and Deletion Constraints Workflow")
    void categoryWorkflow() {
        // 1. Create Hierarchical Structure
        Category electronics = categoryService.createCategory("Electronics", null);
        Category laptops = categoryService.createCategory("Laptops", electronics.id());
        Category smartphones = categoryService.createCategory("Smartphones", electronics.id());

        // 2. Verify Tree
        List<CategoryService.CategoryTreeNode> tree = categoryService.getCategoriesAsTree();
        assertEquals(1, tree.size()); // Just Electronics at root
        assertEquals("Electronics", tree.get(0).category().name());
        assertEquals(2, tree.get(0).subcategories().size());

        // 3. Link Product to Laptops
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                "MacBook Air", "Description", laptops.id(), "MAC-001",
                new BigDecimal("999.00"), new BigDecimal("800.00"), 5,
                ProductStatus.ACTIVE, null, 10, BigDecimal.ZERO, null
        );
        long productId = productService.createProduct(cmd);

        // 4. Try Deletion Constraints
        // Cannot delete Electronics (has subcategories)
        assertThrows(RuntimeException.class, () -> categoryService.deleteCategory(electronics.id()));
        
        // Cannot delete Laptops (has products)
        assertThrows(RuntimeException.class, () -> categoryService.deleteCategory(laptops.id()));

        // Can delete Smartphones (empty)
        assertDoesNotThrow(() -> categoryService.deleteCategory(smartphones.id()));

        // 5. Move Product and Clean Up
        productService.updateProduct(productId, new ProductService.UpdateProductCommand(
                null, null, electronics.id(), null, null, null, null, null, null, null, null, null, null
        ));

        // Now Laptops can be deleted
        assertDoesNotThrow(() -> categoryService.deleteCategory(laptops.id()));

        // Finally, move product to null (Uncategorized) to delete Electronics
        productService.updateProduct(productId, new ProductService.UpdateProductCommand(
                null, null, null, true, null, null, null, null, null, null, null, null, null, null
        ));
        assertDoesNotThrow(() -> categoryService.deleteCategory(electronics.id()));
    }

    @Test
    @Order(2)
    @DisplayName("Unique Name Constraint")
    void duplicateNameTest() {
        categoryService.createCategory("UniqueCat", null);
        assertThrows(RuntimeException.class, () -> categoryService.createCategory("UniqueCat", null));
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

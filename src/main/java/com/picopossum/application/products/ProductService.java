package com.picopossum.application.products;

import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.model.InventoryAdjustment;
import com.picopossum.domain.model.InventoryLot;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.FileStorageService;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.shared.util.TimeUtil;

import java.util.Map;
import java.util.Objects;

/**
 * Core service for Product management.
 * Follows production-grade standards: SOC, performance, and reliability.
 */
public class ProductService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final SettingsStore settingsStore;
    private final ProductValidator validator;
    private final FileStorageService storageService;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager,
                          SettingsStore settingsStore,
                          ProductValidator validator,
                          FileStorageService storageService) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.settingsStore = settingsStore;
        this.validator = validator;
        this.storageService = storageService;
    }

    public long createProduct(CreateProductCommand command) {
        validator.validateCreate(command);
        
        return transactionManager.runInTransaction(() -> {
            String effectiveSku = (command.sku() == null || command.sku().isBlank()) 
                    ? String.valueOf(productRepository.getNextGeneratedNumericSku()) 
                    : command.sku();

            if (productRepository.existsBySku(effectiveSku)) {
                throw new ValidationException("Product with SKU " + effectiveSku + " already exists");
            }

            Product product = new Product(
                    null,
                    command.name(),
                    command.description(),
                    command.categoryId(),
                    null,
                    effectiveSku,
                    command.mrp(),
                    command.costPrice(),
                    command.stockAlertCap() != null ? command.stockAlertCap() : 10,
                    command.status() != null ? command.status() : "active",
                    command.imagePath(),
                    0, // Stock starts at 0, updated below
                    TimeUtil.nowUTC(),
                    TimeUtil.nowUTC(),
                    null
            );

            long productId = productRepository.insertProduct(product);

            logAudit(command.userId(), "CREATE", productId, null, 
                    Map.of("name", command.name(), "sku", effectiveSku, "mrp", command.mrp()));

            if (command.initialStock() != null && command.initialStock() > 0) {
                InventoryLot lot = new InventoryLot(null, productId, null, null, null, command.initialStock(), command.costPrice(), TimeUtil.nowUTC());
                long lotId = inventoryRepository.insertInventoryLot(lot);

                InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, lotId, command.initialStock(), 
                        InventoryReason.CONFIRM_RECEIVE.getValue(), "initial", null, command.userId(), "Initial stock", TimeUtil.nowUTC());
                inventoryRepository.insertInventoryAdjustment(adjustment);

                LoggingConfig.getLogger().info("Initial stock {} added for product {}", command.initialStock(), productId);
            }

            return productId;
        });
    }

    public void updateProduct(long productId, UpdateProductCommand command) {
        validator.validateUpdate(command);
        
        transactionManager.runInTransaction(() -> {
            Product oldProduct = productRepository.findProductById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            if (command.sku() != null && !command.sku().equals(oldProduct.sku())) {
                if (productRepository.existsBySkuExcludeId(command.sku(), productId)) {
                    throw new ValidationException("Product with SKU " + command.sku() + " already exists");
                }
            }

            String imagePath = command.newImagePath() != null ? command.newImagePath() : oldProduct.imagePath();

            Product updatedProduct = new Product(
                    productId,
                    command.name() != null ? command.name() : oldProduct.name(),
                    command.description() != null ? command.description() : oldProduct.description(),
                    command.categoryId() != null ? command.categoryId() : oldProduct.categoryId(),
                    null,
                    command.sku() != null ? command.sku() : oldProduct.sku(),
                    command.mrp() != null ? command.mrp() : oldProduct.mrp(),
                    command.costPrice() != null ? command.costPrice() : oldProduct.costPrice(),
                    command.stockAlertCap() != null ? command.stockAlertCap() : oldProduct.stockAlertCap(),
                    command.status() != null ? command.status() : oldProduct.status(),
                    imagePath,
                    null, null, null, null
            );

            productRepository.updateProductById(productId, updatedProduct);

            if (command.newImagePath() != null && oldProduct.imagePath() != null 
                    && !Objects.equals(command.newImagePath(), oldProduct.imagePath())) {
                storageService.delete(oldProduct.imagePath());
            }

            if (command.stock() != null) {
                int currentStock = inventoryRepository.getStockByProductId(productId);
                int diff = command.stock() - currentStock;

                if (diff != 0) {
                    InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, null, diff, 
                            InventoryReason.CORRECTION.getValue(), "manual", null, command.userId(), 
                            command.stockAdjustmentReason(), TimeUtil.nowUTC());
                    inventoryRepository.insertInventoryAdjustment(adjustment);
                }
            }

            logAudit(command.userId(), "UPDATE", productId, 
                    Map.of("name", oldProduct.name(), "mrp", oldProduct.mrp()),
                    Map.of("name", updatedProduct.name(), "mrp", updatedProduct.mrp()));

            return null;
        });
    }

    public void deleteProduct(long id, long userId) {
        transactionManager.runInTransaction(() -> {
            Product product = productRepository.findProductById(id)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            int currentStock = inventoryRepository.getStockByProductId(id);
            if (currentStock != 0) {
                InventoryAdjustment adjustment = new InventoryAdjustment(null, id, null, -currentStock, 
                        InventoryReason.PRODUCT_DELETED.getValue(), "system", null, userId, "Final cleanup", TimeUtil.nowUTC());
                inventoryRepository.insertInventoryAdjustment(adjustment);
            }

            productRepository.softDeleteProduct(id);
            storageService.delete(product.imagePath());

            logAudit(userId, "DELETE", id, Map.of("name", product.name(), "sku", product.sku()), null);
            return null;
        });
    }

    public Product getProductById(long id) {
        return productRepository.findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public PagedResult<Product> getProducts(ProductFilter filter) {
        return productRepository.findProducts(filter);
    }

    public Map<String, Object> getProductStats() {
        return productRepository.getProductStats();
    }

    public int getNextGeneratedNumericSku() {
        return productRepository.getNextGeneratedNumericSku();
    }

    private void logAudit(long userId, String action, long rowId, Object oldData, Object newData) {
        try {
            com.picopossum.infrastructure.serialization.JsonService js = new com.picopossum.infrastructure.serialization.JsonService();
            auditRepository.log("products", rowId, action, js.toJson(newData), userId);
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Audit logging failed", e);
        }
    }

    public record CreateProductCommand(String name, String description, Long categoryId, String sku, 
                                      java.math.BigDecimal mrp, java.math.BigDecimal costPrice, 
                                      Integer stockAlertCap, String status, String imagePath, 
                                      Integer initialStock, Long userId) {}

    public record UpdateProductCommand(String name, String description, Long categoryId, String sku, 
                                      java.math.BigDecimal mrp, java.math.BigDecimal costPrice, 
                                      Integer stockAlertCap, String status, String newImagePath, 
                                      Integer stock, String stockAdjustmentReason, Long userId) {}
}

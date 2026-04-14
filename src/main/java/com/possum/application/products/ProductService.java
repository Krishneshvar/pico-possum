package com.possum.application.products;

import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Product;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ProductService {
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final AppPaths appPaths;
    private final SettingsStore settingsStore;

    public ProductService(ProductRepository productRepository,
                          InventoryRepository inventoryRepository,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager,
                          AppPaths appPaths,
                          SettingsStore settingsStore) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.appPaths = appPaths;
        this.settingsStore = settingsStore;
    }

    public long createProduct(CreateProductCommand command) {
        if (command.name() == null || command.name().isBlank()) throw new ValidationException("Product name is required");
        if (command.mrp() == null || command.mrp().compareTo(java.math.BigDecimal.ZERO) < 0)
            throw new ValidationException("Product price must be zero or greater");
        if (command.costPrice() == null || command.costPrice().compareTo(java.math.BigDecimal.ZERO) < 0)
            throw new ValidationException("Product cost price must be zero or greater");

        return transactionManager.runInTransaction(() -> {
            boolean autoNumericSku = isNumericSkuGenerationEnabled();
            String effectiveSku = (autoNumericSku && (command.sku() == null || command.sku().isBlank())) 
                    ? String.valueOf(productRepository.getNextGeneratedNumericSku()) 
                    : command.sku();

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
                    null,
                    null,
                    null,
                    null
            );

            long productId = productRepository.insertProduct(product);

            auditRepository.insertAuditLog(createAuditLog(
                    command.userId(),
                    "CREATE",
                    "products",
                    productId,
                    null,
                    String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"mrp\":%s,\"cost_price\":%s}",
                            command.name(), effectiveSku, command.mrp(), command.costPrice())
            ));

            if (command.initialStock() != null && command.initialStock() > 0) {
                InventoryLot lot = new InventoryLot(null, productId, null, null, null, command.initialStock(), command.costPrice(), null, null);
                long lotId = inventoryRepository.insertInventoryLot(lot);

                InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, lotId, command.initialStock(), "confirm_receive", null, null, command.userId(), null, null);
                inventoryRepository.insertInventoryAdjustment(adjustment);

                LoggingConfig.getLogger().info("Initial stock {} added for product {}", command.initialStock(), productId);
            }

            return productId;
        });
    }

    public Product getProductById(long id) {
        return productRepository.findProductById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public int getNextGeneratedNumericSku() {
        return productRepository.getNextGeneratedNumericSku();
    }

    public PagedResult<Product> getProducts(ProductFilter filter) {
        return productRepository.findProducts(filter);
    }

    public Map<String, Object> getProductStats() {
        return productRepository.getProductStats();
    }

    public void updateProduct(long productId, UpdateProductCommand command) {
        transactionManager.runInTransaction(() -> {
            Product oldProduct = productRepository.findProductById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

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
                    null,
                    null,
                    null,
                    null
            );

            int changes = productRepository.updateProductById(productId, updatedProduct);

            if (command.newImagePath() != null && oldProduct.imagePath() != null) {
                deleteImageFile(oldProduct.imagePath());
            }

            if (command.stock() != null) {
                int targetStock = command.stock();
                if (targetStock >= 0) {
                    int currentStock = inventoryRepository.getStockByProductId(productId);
                    int diff = targetStock - currentStock;

                    if (diff != 0) {
                        String reason = command.stockAdjustmentReason() != null ? command.stockAdjustmentReason() : "correction";
                        InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, null, diff, reason, null, null, command.userId(), null, null);
                        inventoryRepository.insertInventoryAdjustment(adjustment);
                        LoggingConfig.getLogger().info("Stock adjusted for product {}: {} (reason: {})",
                                productId, (diff > 0 ? "+" : "") + diff, reason);
                    }
                }
            }

            if (changes > 0) {
                auditRepository.insertAuditLog(createAuditLog(
                        command.userId(),
                        "UPDATE",
                        "products",
                        productId,
                        String.format("{\"name\":\"%s\",\"mrp\":%s}", oldProduct.name(), oldProduct.mrp()),
                        String.format("{\"name\":\"%s\",\"mrp\":%s}", updatedProduct.name(), updatedProduct.mrp())
                ));
            }

            return null;
        });
    }

    public void deleteProduct(long id, long userId) {
        transactionManager.runInTransaction(() -> {
            Product oldProduct = productRepository.findProductById(id)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            int currentStock = inventoryRepository.getStockByProductId(id);
            if (currentStock != 0) {
                InventoryAdjustment adjustment = new InventoryAdjustment(null, id, null, -currentStock, InventoryReason.PRODUCT_DELETED.getValue(), null, null, userId, null, null);
                inventoryRepository.insertInventoryAdjustment(adjustment);
            }

            int changes = productRepository.softDeleteProduct(id);

            if (changes > 0 && oldProduct.imagePath() != null) {
                deleteImageFile(oldProduct.imagePath());
            }

            if (changes > 0) {
                auditRepository.insertAuditLog(createAuditLog(
                        userId,
                        "DELETE",
                        "products",
                        id,
                        String.format("{\"name\":\"%s\",\"sku\":\"%s\"}", oldProduct.name(), oldProduct.sku()),
                        null
                ));
            }

            return null;
        });
    }

    private void deleteImageFile(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            LoggingConfig.getLogger().error("Failed to delete product image: {}", imagePath, e);
        }
    }

    private com.possum.domain.model.AuditLog createAuditLog(long userId, String action, String tableName, long rowId, String oldData, String newData) {
        return new com.possum.domain.model.AuditLog(null, userId, action, tableName, rowId, oldData, newData, null, null, null);
    }

    private boolean isNumericSkuGenerationEnabled() {
        try {
            return settingsStore.loadGeneralSettings().isNumericalSkuGenerationEnabled();
        } catch (Exception ex) {
            return false;
        }
    }

    public record CreateProductCommand(
            String name,
            String description,
            Long categoryId,
            String sku,
            java.math.BigDecimal mrp,
            java.math.BigDecimal costPrice,
            Integer stockAlertCap,
            String status,
            String imagePath,
            Integer initialStock,
            Long userId
    ) {}

    public record UpdateProductCommand(
            String name,
            String description,
            Long categoryId,
            String sku,
            java.math.BigDecimal mrp,
            java.math.BigDecimal costPrice,
            Integer stockAlertCap,
            String status,
            String newImagePath,
            Integer stock,
            String stockAdjustmentReason,
            Long userId
    ) {}
}

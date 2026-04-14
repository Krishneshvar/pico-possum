package com.possum.application.products;

import com.possum.application.variants.VariantService;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Product;
import com.possum.domain.model.TaxRule;
import com.possum.domain.model.Variant;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.domain.repositories.VariantRepository;
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
    private final VariantService variantService;
    private final VariantRepository variantRepository;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final AppPaths appPaths;
    private final SettingsStore settingsStore;

    public ProductService(ProductRepository productRepository,
                          VariantService variantService,
                          VariantRepository variantRepository,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager,
                          AppPaths appPaths,
                          SettingsStore settingsStore) {
        this.productRepository = productRepository;
        this.variantService = variantService;
        this.variantRepository = variantRepository;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.appPaths = appPaths;
        this.settingsStore = settingsStore;
    }

    public long createProductWithVariants(CreateProductCommand command) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PRODUCTS_MANAGE);
        if (command.name() == null || command.name().isBlank()) throw new ValidationException("Product name is required");
        if (command.variants() == null || command.variants().isEmpty()) {
            throw new ValidationException("At least one variant is required");
        }

        return transactionManager.runInTransaction(() -> {
            boolean autoNumericSku = isNumericSkuGenerationEnabled();
            int nextGeneratedSku = autoNumericSku ? variantRepository.getNextGeneratedNumericSku() : -1;

            Product product = new Product(
                    null,
                    command.name(),
                    command.description(),
                    command.categoryId(),
                    null,
                    command.taxIds() != null && !command.taxIds().isEmpty() ? command.taxIds().get(0) : null,
                    null,
                    command.status() != null ? command.status() : "active",
                    command.imagePath(),
                    null,
                    null,
                    null,
                    null
            );

            long productId = productRepository.insertProduct(product);

            for (var variantCmd : command.variants()) {
                if (variantCmd.name() == null || variantCmd.name().isBlank())
                    throw new ValidationException("Variant name is required");
                if (variantCmd.price() == null || variantCmd.price().compareTo(java.math.BigDecimal.ZERO) < 0)
                    throw new ValidationException("Variant price must be zero or greater");
                if (variantCmd.costPrice() == null || variantCmd.costPrice().compareTo(java.math.BigDecimal.ZERO) < 0)
                    throw new ValidationException("Variant cost price must be zero or greater");

                String effectiveSku = autoNumericSku ? String.valueOf(nextGeneratedSku++) : variantCmd.sku();
                variantService.addVariantWithoutTransaction(new VariantService.AddVariantCommand(
                        productId,
                        variantCmd.name(),
                        effectiveSku,
                        variantCmd.price(),
                        variantCmd.costPrice(),
                        variantCmd.stockAlertCap(),
                        variantCmd.isDefault(),
                        variantCmd.status(),
                        variantCmd.stock(),
                        command.userId()
                ));
            }

            auditRepository.insertAuditLog(createAuditLog(
                    command.userId(),
                    "CREATE",
                    "products",
                    productId,
                    null,
                    String.format("{\"name\":\"%s\",\"description\":\"%s\",\"category_id\":%s,\"status\":\"%s\",\"image_path\":\"%s\"}",
                            command.name(), command.description(), command.categoryId(), command.status(), command.imagePath())
            ));

            return productId;
        });
    }

    public ProductWithVariantsDTO getProductWithVariants(long id) {
        var result = productRepository.findProductWithVariants(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        List<TaxRule> taxes = productRepository.findProductTaxes(id);

        return new ProductWithVariantsDTO(result.product(), result.variants(), taxes);
    }

    public PagedResult<Product> getProducts(ProductFilter filter) {
        return productRepository.findProducts(filter);
    }

    public Map<String, Object> getProductStats() {
        return productRepository.getProductStats();
    }

    public Product getProductById(long id) {
        return productRepository.findProductById(id).orElse(null);
    }

    public void updateProduct(long productId, UpdateProductCommand command) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PRODUCTS_MANAGE);
        transactionManager.runInTransaction(() -> {
            boolean autoNumericSku = isNumericSkuGenerationEnabled();
            int nextGeneratedSku = autoNumericSku ? variantRepository.getNextGeneratedNumericSku() : -1;

            Product oldProduct = productRepository.findProductById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            String imagePath = command.newImagePath() != null ? command.newImagePath() : oldProduct.imagePath();
            Long taxCategoryId = oldProduct.taxCategoryId();

            if (command.taxIds() != null) {
                taxCategoryId = command.taxIds() != null && !command.taxIds().isEmpty() ? command.taxIds().get(0) : null;
            }

            if (command.variants() != null) {
                for (var variantCmd : command.variants()) {
                    if (variantCmd.id() != null) {
                        variantService.validateVariantOwnership(variantCmd.id(), productId);
                        String effectiveSku = variantCmd.sku();
                        if (autoNumericSku) {
                            Variant existingVariant = variantRepository.findVariantByIdSync(variantCmd.id())
                                    .orElseThrow(() -> new NotFoundException("Variant not found: " + variantCmd.id()));
                            if (existingVariant.sku() == null || existingVariant.sku().trim().isEmpty()) {
                                effectiveSku = String.valueOf(nextGeneratedSku++);
                            } else {
                                effectiveSku = existingVariant.sku();
                            }
                        }
                        variantService.updateVariantWithoutTransaction(new VariantService.UpdateVariantCommand(
                                variantCmd.id(),
                                variantCmd.name(),
                                effectiveSku,
                                variantCmd.price(),
                                variantCmd.costPrice(),
                                variantCmd.stockAlertCap(),
                                variantCmd.isDefault(),
                                variantCmd.status(),
                                variantCmd.stock(),
                                variantCmd.stockAdjustmentReason(),
                                command.userId()
                        ));
                    } else {
                        String effectiveSku = autoNumericSku ? String.valueOf(nextGeneratedSku++) : variantCmd.sku();
                        variantService.addVariantWithoutTransaction(new VariantService.AddVariantCommand(
                                productId,
                                variantCmd.name(),
                                effectiveSku,
                                variantCmd.price(),
                                variantCmd.costPrice(),
                                variantCmd.stockAlertCap(),
                                variantCmd.isDefault(),
                                variantCmd.status(),
                                variantCmd.stock(),
                                command.userId()
                        ));
                    }
                }
            }

            Product updatedProduct = new Product(
                    productId,
                    command.name() != null ? command.name() : oldProduct.name(),
                    command.description() != null ? command.description() : oldProduct.description(),
                    command.categoryId() != null ? command.categoryId() : oldProduct.categoryId(),
                    null,
                    taxCategoryId,
                    null,
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

            if (changes > 0) {
                Product newProduct = productRepository.findProductById(productId).orElse(null);
                auditRepository.insertAuditLog(createAuditLog(
                        command.userId(),
                        "UPDATE",
                        "products",
                        productId,
                        String.format("{\"name\":\"%s\",\"description\":\"%s\"}", oldProduct.name(), oldProduct.description()),
                        String.format("{\"name\":\"%s\",\"description\":\"%s\"}", newProduct.name(), newProduct.description())
                ));
            }

            return null;
        });
    }

    public int getNextGeneratedNumericSku() {
        return variantRepository.getNextGeneratedNumericSku();
    }

    public void deleteProduct(long id, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PRODUCTS_MANAGE);
        transactionManager.runInTransaction(() -> {
            Product oldProduct = productRepository.findProductById(id)
                    .orElseThrow(() -> new NotFoundException("Product not found"));

            int changes = productRepository.softDeleteProduct(id);

            if (changes > 0 && oldProduct.imagePath() != null) {
                deleteImageFile(oldProduct.imagePath());
            }

            if (changes > 0) {
                // Fetch and delete all variants, adjusting their stock to 0
                productRepository.findProductWithVariants(id).ifPresent(dto -> {
                    for (com.possum.domain.model.Variant variant : dto.variants()) {
                        try {
                            // Adjust stock to 0
                            variantService.updateVariantWithoutTransaction(new VariantService.UpdateVariantCommand(
                                    variant.id(),
                                    variant.name(),
                                    variant.sku(),
                                    variant.price(),
                                    variant.costPrice(),
                                    variant.stockAlertCap(),
                                    variant.defaultVariant(),
                                    variant.status(),
                                    0,
                                    "product_deleted",
                                    userId
                            ));
                            // Soft delete variant
                            variantService.deleteVariant(variant.id(), userId);
                        } catch (Exception e) {
                            LoggingConfig.getLogger().error("Failed to delete variant {} while deleting product {}: {}", 
                                variant.id(), id, e.getMessage());
                        }
                    }
                });

                auditRepository.insertAuditLog(createAuditLog(
                        userId,
                        "DELETE",
                        "products",
                        id,
                        String.format("{\"name\":\"%s\",\"description\":\"%s\"}", oldProduct.name(), oldProduct.description()),
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
            String status,
            String imagePath,
            List<VariantCommand> variants,
            List<Long> taxIds,
            Long userId
    ) {}

    public record UpdateProductCommand(
            String name,
            String description,
            Long categoryId,
            String status,
            String newImagePath,
            List<VariantCommand> variants,
            List<Long> taxIds,
            Long userId
    ) {}

    public record VariantCommand(
            Long id,
            String name,
            String sku,
            java.math.BigDecimal price,
            java.math.BigDecimal costPrice,
            Integer stockAlertCap,
            Boolean isDefault,
            String status,
            Integer stock,
            String stockAdjustmentReason
    ) {}

    public record ProductWithVariantsDTO(
            Product product,
            List<Variant> variants,
            List<TaxRule> taxes
    ) {}
}

package com.possum.application.variants;

import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Variant;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.domain.repositories.VariantRepository;
import com.possum.shared.dto.PagedResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class VariantService {
    private final VariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;

    public VariantService(VariantRepository variantRepository,
                          InventoryRepository inventoryRepository,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager) {
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
    }

    public long addVariant(AddVariantCommand command) {
        if (command.productId() == null || command.name() == null || command.price() == null || command.costPrice() == null || command.userId() == null) {
            throw new ValidationException("Product ID, name, price, cost_price, and userId are required");
        }

        if (command.price().compareTo(BigDecimal.ZERO) < 0 || command.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Price and cost_price must be non-negative");
        }

        return transactionManager.runInTransaction(() -> addVariantWithoutTransaction(command));
    }

    public long addVariantWithoutTransaction(AddVariantCommand command) {
        Variant variant = new Variant(
                null,
                command.productId(),
                null,
                command.name(),
                command.sku(),
                command.price(),
                command.costPrice(),
                command.stockAlertCap() != null ? command.stockAlertCap() : 10,
                command.isDefault() != null && command.isDefault(),
                command.status() != null ? command.status() : "active",
                null, 
                null,
                null,
                null,
                null,
                null,
                null
        );

        long variantId = variantRepository.insertVariant(command.productId(), variant);

        auditRepository.insertAuditLog(createAuditLog(
                command.userId(),
                "CREATE",
                "variants",
                variantId,
                null,
                String.format("{\"product_id\":%d,\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"cost_price\":%s}",
                        command.productId(), command.name(), command.sku(), command.price(), command.costPrice())
        ));

        if (command.stock() != null && command.stock() > 0) {
            InventoryLot lot = new InventoryLot(null, variantId, null, null, null, command.stock(), command.costPrice(), null, null);
            long lotId = inventoryRepository.insertInventoryLot(lot);

            InventoryAdjustment adjustment = new InventoryAdjustment(null, variantId, lotId, command.stock(), "confirm_receive", null, null, command.userId(), null, null);
            inventoryRepository.insertInventoryAdjustment(adjustment);

            LoggingConfig.getLogger().info("Initial stock {} added for variant {}", command.stock(), variantId);
        }

        return variantId;
    }

    public void updateVariant(UpdateVariantCommand command) {
        if (command.id() == null || command.name() == null || command.price() == null || command.costPrice() == null || command.userId() == null) {
            throw new ValidationException("ID, name, price, cost_price, and userId are required");
        }

        if (command.price().compareTo(BigDecimal.ZERO) < 0 || command.costPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Price and cost_price must be non-negative");
        }

        Variant existing = variantRepository.findVariantByIdSync(command.id())
                .orElseThrow(() -> new ValidationException("Variant not found"));

        transactionManager.runInTransaction(() -> {
            updateVariantWithoutTransaction(command, existing);
            return null;
        });
    }

    public void updateVariantWithoutTransaction(UpdateVariantCommand command) {
        Variant existing = variantRepository.findVariantByIdSync(command.id())
                .orElseThrow(() -> new ValidationException("Variant not found"));
        updateVariantWithoutTransaction(command, existing);
    }

    private void updateVariantWithoutTransaction(UpdateVariantCommand command, Variant existing) {
        Variant variant = new Variant(
                command.id(),
                existing.productId(),
                null,
                command.name(),
                command.sku(),
                command.price(),
                command.costPrice(),
                command.stockAlertCap() != null ? command.stockAlertCap() : 10,
                command.isDefault() != null && command.isDefault(),
                command.status() != null ? command.status() : "active",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        variantRepository.updateVariantById(variant);

        auditRepository.insertAuditLog(createAuditLog(
                command.userId(),
                "UPDATE",
                "variants",
                command.id(),
                String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"cost_price\":%s}",
                        existing.name(), existing.sku(), existing.price(), existing.costPrice()),
                String.format("{\"name\":\"%s\",\"sku\":\"%s\",\"price\":%s,\"cost_price\":%s}",
                        command.name(), command.sku(), command.price(), command.costPrice())
        ));

        if (command.stock() != null) {
            int targetStock = command.stock();
            if (targetStock >= 0) {
                int currentStock = inventoryRepository.getStockByVariantId(command.id());
                int diff = targetStock - currentStock;

                if (diff != 0) {
                    String reason = command.stockAdjustmentReason() != null ? command.stockAdjustmentReason() : "correction";
                    InventoryAdjustment adjustment = new InventoryAdjustment(null, command.id(), null, diff, reason, null, null, command.userId(), null, null);
                    inventoryRepository.insertInventoryAdjustment(adjustment);
                    LoggingConfig.getLogger().info("Stock adjusted for variant {}: {} (reason: {})",
                            command.id(), (diff > 0 ? "+" : "") + diff, reason);
                }
            }
        }
    }

    public void deleteVariant(long id, long userId) {
        Variant existing = variantRepository.findVariantByIdSync(id)
                .orElseThrow(() -> new ValidationException("Variant not found"));

        int changes = variantRepository.softDeleteVariant(id);

        auditRepository.insertAuditLog(createAuditLog(
                userId,
                "DELETE",
                "variants",
                id,
                String.format("{\"name\":\"%s\",\"sku\":\"%s\"}", existing.name(), existing.sku()),
                null
        ));

        LoggingConfig.getLogger().info("Variant {} soft deleted by user {}", id, userId);
    }

    public PagedResult<Variant> getVariants(VariantFilterCriteria criteria) {
        return variantRepository.findVariants(
                criteria.searchTerm(),
                criteria.categoryId(),
                criteria.categories(),
                criteria.taxCategories(),
                criteria.stockStatus(),
                criteria.status(),
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.sortBy() != null ? criteria.sortBy() : "p.name",
                criteria.sortOrder() != null ? criteria.sortOrder() : "ASC",
                criteria.currentPage() != null ? criteria.currentPage() : 1,
                criteria.itemsPerPage() != null ? criteria.itemsPerPage() : 10
        );
    }

    public Map<String, Object> getVariantStats() {
        return variantRepository.getVariantStats();
    }

    public void validateVariantOwnership(long variantId, long productId) {
        Variant variant = variantRepository.findVariantByIdSync(variantId)
                .orElseThrow(() -> new NotFoundException("Variant " + variantId + " not found"));
        if (!variant.productId().equals(productId)) {
            throw new ValidationException("Variant " + variantId + " does not belong to product " + productId);
        }
    }

    private com.possum.domain.model.AuditLog createAuditLog(long userId, String action, String tableName, long rowId, String oldData, String newData) { return new com.possum.domain.model.AuditLog(null, userId, action, tableName, rowId, oldData, newData, null, null, null);
    }

    public record AddVariantCommand(
            Long productId,
            String name,
            String sku,
            BigDecimal price,
            BigDecimal costPrice,
            Integer stockAlertCap,
            Boolean isDefault,
            String status,
            Integer stock,
            Long userId
    ) {}

    public record UpdateVariantCommand(
            Long id,
            String name,
            String sku,
            BigDecimal price,
            BigDecimal costPrice,
            Integer stockAlertCap,
            Boolean isDefault,
            String status,
            Integer stock,
            String stockAdjustmentReason,
            Long userId
    ) {}

    public record VariantFilterCriteria(
            String searchTerm,
            Long categoryId,
            List<Long> categories,
            List<Long> taxCategories,
            List<String> stockStatus,
            List<String> status,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            String sortOrder,
            Integer currentPage,
            Integer itemsPerPage
    ) {}
}

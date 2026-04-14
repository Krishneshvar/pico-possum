package com.possum.application.inventory;

import com.possum.domain.services.StockManager;

import com.possum.domain.enums.FlowEventType;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Product;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.shared.dto.AvailableLot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.possum.shared.util.TimeUtil;
import java.util.List;
import java.util.Map;

public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductFlowService productFlowService;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;
    private final StockManager stockManager;

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductFlowService productFlowService,
                            AuditRepository auditRepository,
                            TransactionManager transactionManager,
                            JsonService jsonService,
                            SettingsStore settingsStore,
                            StockManager stockManager) {
        this.inventoryRepository = inventoryRepository;
        this.productFlowService = productFlowService;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
        this.stockManager = stockManager;
    }

    public int getProductStock(long productId) {
        return inventoryRepository.getStockByProductId(productId);
    }

    public List<InventoryLot> getProductLots(long productId) {
        return inventoryRepository.findLotsByProductId(productId);
    }

    public List<InventoryAdjustment> getProductAdjustments(long productId, int limit, int offset) {
        return inventoryRepository.findAdjustmentsByProductId(productId, limit, offset);
    }

    public List<com.possum.shared.dto.StockHistoryDto> getStockHistory(String search, List<String> reasons, String fromDate, String toDate, List<Long> userIds, int limit, int offset) {
        return inventoryRepository.findStockHistory(search, reasons, fromDate, toDate, userIds, limit, offset);
    }

    public List<Product> getLowStockAlerts() {
        return inventoryRepository.findLowStockProducts();
    }

    public List<InventoryLot> getExpiringLots(int days) {
        return inventoryRepository.findExpiringLots(days);
    }

    public Map<String, Object> getInventoryStats() {
        return inventoryRepository.getInventoryStats();
    }

    public ReceiveInventoryResult receiveInventory(long productId, int quantity, BigDecimal unitCost,
                                                   String batchNumber, LocalDateTime manufacturedDate,
                                                   LocalDateTime expiryDate, Long purchaseOrderItemId, long userId) {
        if (quantity <= 0)
            throw new com.possum.domain.exceptions.ValidationException("Quantity must be positive");
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0)
            throw new com.possum.domain.exceptions.ValidationException("Unit cost must be zero or greater");

        return transactionManager.runInTransaction(() -> {
            InventoryLot lot = new InventoryLot(null, productId, batchNumber, manufacturedDate, expiryDate,
                    quantity, unitCost, purchaseOrderItemId, TimeUtil.nowUTC());
            long lotId = inventoryRepository.insertInventoryLot(lot);

            InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, lotId, quantity,
                    InventoryReason.CONFIRM_RECEIVE.getValue(), "purchase_order_item", purchaseOrderItemId,
                    userId, null, TimeUtil.nowUTC());
            inventoryRepository.insertInventoryAdjustment(adjustment);

            int newStock = inventoryRepository.getStockByProductId(productId);

            productFlowService.logProductFlow(productId, FlowEventType.PURCHASE, quantity,
                    "purchase_order_item", purchaseOrderItemId);

            Map<String, Object> auditData = Map.of(
                    "product_id", productId,
                    "quantity", quantity,
                    "unit_cost", unitCost,
                    "batch_number", batchNumber != null ? batchNumber : "",
                    "new_stock", newStock
            );
            auditRepository.log("inventory_lots", lotId, "CREATE", jsonService.toJson(auditData), userId);

            return new ReceiveInventoryResult(lotId, productId, quantity, newStock);
        });
    }

    public DeductStockResult deductStock(long productId, int quantity, long userId, InventoryReason reason,
                                         String referenceType, Long referenceId) {
        if (quantity <= 0) {
            return new DeductStockResult(true, 0);
        }

        int currentStock = inventoryRepository.getStockByProductId(productId);
        if (isInventoryRestrictionsEnabled() && currentStock < quantity) {
            throw new InsufficientStockException(currentStock, quantity);
        }

        return transactionManager.runInTransaction(() -> {
            deductStockInternal(productId, quantity, userId, reason, referenceType, referenceId);
            return new DeductStockResult(true, quantity);
        });
    }

    public RestoreStockResult restoreStock(long productId, String referenceType, long referenceId,
                                           int quantity, long userId, InventoryReason reason,
                                           String newReferenceType, Long newReferenceId) {
        if (quantity <= 0) {
            return new RestoreStockResult(true, 0);
        }

        return transactionManager.runInTransaction(() -> {
            List<InventoryAdjustment> originalAdjustments = inventoryRepository
                    .findAdjustmentsByReference(referenceType, referenceId);

            List<InventoryAdjustment> plan = stockManager.planRestoration(
                    productId, quantity, originalAdjustments, reason, newReferenceType, newReferenceId, userId
            );

            for (InventoryAdjustment adj : plan) {
                inventoryRepository.insertInventoryAdjustment(adj);
            }

            FlowEventType eventType = reason == InventoryReason.RETURN ? FlowEventType.RETURN : FlowEventType.ADJUSTMENT;
            productFlowService.logProductFlow(productId, eventType, quantity,
                    newReferenceType != null ? newReferenceType : "adjustment", newReferenceId);

            return new RestoreStockResult(true, quantity);
        });
    }

    public AdjustInventoryResult adjustInventory(long productId, Long lotId, int quantityChange,
                                                 InventoryReason reason, String referenceType,
                                                 Long referenceId, long userId) {
        if (quantityChange < 0 && isInventoryRestrictionsEnabled()) {
            int currentStock = inventoryRepository.getStockByProductId(productId);
            if (currentStock + quantityChange < 0) {
                throw new InsufficientStockException(currentStock, Math.abs(quantityChange));
            }
        }

        return transactionManager.runInTransaction(() -> {
            if (quantityChange < 0 && lotId == null) {
                deductStockInternal(productId, Math.abs(quantityChange), userId, reason, referenceType, referenceId);
                int newStock = inventoryRepository.getStockByProductId(productId);
                return new AdjustInventoryResult(0, productId, quantityChange, reason, newStock);
            }

            InventoryAdjustment adjustment = new InventoryAdjustment(null, productId, lotId, quantityChange,
                    reason.getValue(), referenceType, referenceId, userId, null, TimeUtil.nowUTC());
            long adjustmentId = inventoryRepository.insertInventoryAdjustment(adjustment);

            int newStock = inventoryRepository.getStockByProductId(productId);

            FlowEventType eventType = switch (reason) {
                case SALE -> FlowEventType.SALE;
                case RETURN -> FlowEventType.RETURN;
                default -> FlowEventType.ADJUSTMENT;
            };

            productFlowService.logProductFlow(productId, eventType, quantityChange,
                    referenceType != null ? referenceType : "adjustment", referenceId != null ? referenceId : adjustmentId);

            Map<String, Object> auditData = Map.of(
                    "product_id", productId,
                    "quantity_change", quantityChange,
                    "reason", reason.getValue(),
                    "new_stock", newStock
            );
            auditRepository.log("inventory_adjustments", adjustmentId, "CREATE", jsonService.toJson(auditData), userId);

            return new AdjustInventoryResult(adjustmentId, productId, quantityChange, reason, newStock);
        });
    }

    private void deductStockInternal(long productId, int quantity, long userId, InventoryReason reason,
                                     String referenceType, Long referenceId) {
        List<AvailableLot> availableLots = inventoryRepository.findAvailableLotsByProductId(productId);
        List<InventoryAdjustment> plan = stockManager.planDeduction(
                productId, quantity, availableLots, reason, referenceType, referenceId, userId
        );

        for (InventoryAdjustment adj : plan) {
            inventoryRepository.insertInventoryAdjustment(adj);
        }

        FlowEventType eventType = reason == InventoryReason.SALE ? FlowEventType.SALE : FlowEventType.ADJUSTMENT;
        productFlowService.logProductFlow(productId, eventType, -quantity,
                referenceType != null ? referenceType : "adjustment", referenceId);
    }

    public record ReceiveInventoryResult(long lotId, long productId, int quantity, int newStock) {}
    public record DeductStockResult(boolean success, int deducted) {}
    public record RestoreStockResult(boolean success, int restored) {}
    public record AdjustInventoryResult(long id, long productId, int quantityChange, InventoryReason reason, int newStock) {}

    private boolean isInventoryRestrictionsEnabled() {
        try {
            return settingsStore.loadGeneralSettings().isInventoryAlertsAndRestrictionsEnabled();
        } catch (Exception ex) {
            return true;
        }
    }
}

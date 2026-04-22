package com.picopossum.application.inventory;

import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.exceptions.InsufficientStockException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.StockMovement;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.shared.util.TimeUtil;
import com.picopossum.shared.dto.StockHistoryDto;

import java.util.List;
import java.util.Map;

/**
 * Modernized Inventory Service for SMB standalone use.
 * Removed lot-based complexity and user identity overhead.
 */
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final ProductFlowService productFlowService;
    private final AuditService auditService;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductFlowService productFlowService,
                            AuditService auditService,
                            TransactionManager transactionManager,
                            JsonService jsonService,
                            SettingsStore settingsStore) {
        this.inventoryRepository = inventoryRepository;
        this.productFlowService = productFlowService;
        this.auditService = auditService;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
    }

    public int getProductStock(long productId) {
        return inventoryRepository.getStockByProductId(productId);
    }

    public List<StockMovement> getProductMovements(long productId, int limit, int offset) {
        return inventoryRepository.findMovementsByProductId(productId, limit, offset);
    }

    public List<StockHistoryDto> getStockHistory(String search, List<String> reasons, String fromDate, String toDate, int limit, int offset) {
        return inventoryRepository.findStockHistory(search, reasons, fromDate, toDate, limit, offset);
    }

    public List<Product> getLowStockAlerts() {
        return inventoryRepository.findLowStockProducts();
    }

    public Map<String, Object> getInventoryStats() {
        return inventoryRepository.getInventoryStats();
    }

    /**
     * Simplified inventory receiving for SMB.
     */
    public long receiveInventory(long productId, int quantity, String notes) {
        if (quantity <= 0) throw new ValidationException("Quantity must be positive");

        return transactionManager.runInTransaction(() -> {
            StockMovement movement = new StockMovement(
                    null, productId, quantity, 
                    InventoryReason.RECEIVE.getValue(), "manual", 
                    null, notes, TimeUtil.nowUTC()
            );
            
            long movementId = inventoryRepository.insertStockMovement(movement);
            
            // Handled by DB Trigger automatically for ProductFlow and Cache
            // But we can log manually if we want extra auditing
            
            Map<String, Object> auditData = Map.of(
                    "product_id", productId,
                    "quantity", quantity,
                    "new_stock", inventoryRepository.getStockByProductId(productId)
            );
            auditService.logCreate("stock_movements", movementId, auditData);

            return movementId;
        });
    }

    public void deductStock(long productId, int quantity, InventoryReason reason,
                                         String referenceType, Long referenceId) {
        if (quantity <= 0) return;

        int currentStock = inventoryRepository.getStockByProductId(productId);
        if (isInventoryRestrictionsEnabled() && currentStock < quantity) {
            throw new InsufficientStockException(currentStock, quantity);
        }

        transactionManager.runInTransaction(() -> {
            StockMovement movement = new StockMovement(
                    null, productId, -quantity, 
                    reason.getValue(), referenceType, 
                    referenceId, null, TimeUtil.nowUTC()
            );
            inventoryRepository.insertStockMovement(movement);
            return null;
        });
    }

    public void adjustInventory(long productId, int quantityChange,
                                                 InventoryReason reason, String referenceType,
                                                 Long referenceId, String notes) {
        if (quantityChange < 0 && isInventoryRestrictionsEnabled()) {
            int currentStock = inventoryRepository.getStockByProductId(productId);
            if (currentStock + quantityChange < 0) {
                throw new InsufficientStockException(currentStock, Math.abs(quantityChange));
            }
        }

        transactionManager.runInTransaction(() -> {
            StockMovement movement = new StockMovement(
                    null, productId, quantityChange, 
                    reason.getValue(), referenceType, 
                    referenceId, notes, TimeUtil.nowUTC()
            );
            long movementId = inventoryRepository.insertStockMovement(movement);

            Map<String, Object> auditData = Map.of(
                    "product_id", productId,
                    "quantity_change", quantityChange,
                    "reason", reason.getValue(),
                    "new_stock", inventoryRepository.getStockByProductId(productId)
            );
            auditService.logCreate("stock_movements", movementId, auditData);

            return null;
        });
    }

    private boolean isInventoryRestrictionsEnabled() {
        try {
            return settingsStore.loadGeneralSettings().isInventoryAlertsAndRestrictionsEnabled();
        } catch (Exception ex) {
            return true;
        }
    }
}

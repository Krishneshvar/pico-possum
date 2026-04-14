package com.possum.domain.repositories;

import com.possum.domain.model.InventoryAdjustment;
import com.possum.domain.model.InventoryLot;
import com.possum.domain.model.Product;
import com.possum.shared.dto.AvailableLot;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InventoryRepository {
    int getStockByProductId(long productId);

    List<InventoryLot> findLotsByProductId(long productId);

    List<AvailableLot> findAvailableLotsByProductId(long productId);

    List<InventoryAdjustment> findAdjustmentsByProductId(long productId, int limit, int offset);

    List<com.possum.shared.dto.StockHistoryDto> findStockHistory(String search, java.util.List<String> reasons, String fromDate, String toDate, java.util.List<Long> userIds, int limit, int offset);

    List<InventoryAdjustment> findAdjustmentsByReference(String referenceType, long referenceId);

    long insertInventoryLot(InventoryLot lot);

    long insertInventoryAdjustment(InventoryAdjustment adjustment);

    Optional<InventoryLot> findLotById(long id);

    List<Product> findLowStockProducts();

    List<InventoryLot> findExpiringLots(int days);

    Map<String, Object> getInventoryStats();
}

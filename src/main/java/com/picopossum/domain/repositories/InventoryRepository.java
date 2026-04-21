package com.picopossum.domain.repositories;

import com.picopossum.domain.model.StockMovement;
import com.picopossum.domain.model.Product;
import com.picopossum.shared.dto.StockHistoryDto;

import java.util.List;
import java.util.Map;

/**
 * Minimalist repository for inventory tracking.
 * Standardized for SMB standalone use cases.
 */
public interface InventoryRepository {
    
    /**
     * @return Aggregate current stock from cache.
     */
    int getStockByProductId(long productId);

    /**
     * @return Paginated stock movements (Sales, Returns, Corrections).
     */
    List<StockMovement> findMovementsByProductId(long productId, int limit, int offset);

    /**
     * Unified stock history search for reporting.
     */
    List<StockHistoryDto> findStockHistory(String search, List<String> reasons, String fromDate, String toDate, int limit, int offset);

    /**
     * Records a new stock change.
     */
    long insertStockMovement(StockMovement movement);

    /**
     * Finds products where cached stock is below alert threshold.
     */
    List<Product> findLowStockProducts();

    /**
     * Statistics for dashboard widgets.
     */
    Map<String, Object> getInventoryStats();
}

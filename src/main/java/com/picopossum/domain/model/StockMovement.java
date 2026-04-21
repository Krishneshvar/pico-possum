package com.picopossum.domain.model;

import java.time.LocalDateTime;

/**
 * Unified model for all inventory changes.
 * Minimalist version for Single-User SMB.
 */
public record StockMovement(
        Long id,
        Long productId,
        Integer quantityChange,
        String reason,
        String referenceType,
        Long referenceId,
        String notes,
        LocalDateTime createdAt
) {
    public StockMovement {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required for stock movement");
        }
        if (quantityChange == null || quantityChange == 0) {
            throw new IllegalArgumentException("Stock movement quantity must be non-zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Stock movement reason must be provided");
        }
    }
}

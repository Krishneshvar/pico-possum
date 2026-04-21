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
}

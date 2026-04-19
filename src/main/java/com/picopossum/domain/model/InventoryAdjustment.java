package com.picopossum.domain.model;

import java.time.LocalDateTime;

public record InventoryAdjustment(
        Long id,
        Long productId,
        Long lotId,
        Integer quantityChange,
        String reason,
        String referenceType,
        Long referenceId,
        Long adjustedBy,
        String adjustedByName,
        LocalDateTime adjustedAt
) {
}

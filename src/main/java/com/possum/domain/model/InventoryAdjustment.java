package com.possum.domain.model;

import java.time.LocalDateTime;

public record InventoryAdjustment(
        Long id,
        Long variantId,
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

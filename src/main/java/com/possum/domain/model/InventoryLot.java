package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryLot(
        Long id,
        Long variantId,
        String batchNumber,
        LocalDateTime manufacturedDate,
        LocalDateTime expiryDate,
        Integer quantity,
        BigDecimal unitCost,
        Long purchaseOrderItemId,
        LocalDateTime createdAt
) {
}

package com.possum.shared.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AvailableLot(
        Long id,
        Long variantId,
        String batchNumber,
        LocalDateTime manufacturedDate,
        LocalDateTime expiryDate,
        Integer initialQuantity,
        BigDecimal unitCost,
        Long purchaseOrderItemId,
        LocalDateTime createdAt,
        Integer remainingQuantity
) {
}

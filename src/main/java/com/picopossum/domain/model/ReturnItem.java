package com.picopossum.domain.model;

import java.math.BigDecimal;

public record ReturnItem(
        Long id,
        Long returnId,
        Long saleItemId,
        Integer quantity,
        BigDecimal refundAmount,
        Long productId,
        BigDecimal pricePerUnit,
        String sku,
        String productName
) {
    public ReturnItem {
        if (returnId == null) throw new IllegalArgumentException("Return ID is required");
        if (quantity == null || quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Refund amount cannot be negative");
        }
    }
}

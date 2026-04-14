package com.possum.domain.model;

import java.math.BigDecimal;

public record ReturnItem(
        Long id,
        Long returnId,
        Long saleItemId,
        Integer quantity,
        BigDecimal refundAmount,
        Long productId,
        BigDecimal pricePerUnit,
        BigDecimal taxRate,
        String sku,
        String productName
) {
}

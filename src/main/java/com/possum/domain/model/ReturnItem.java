package com.possum.domain.model;

import java.math.BigDecimal;

public record ReturnItem(
        Long id,
        Long returnId,
        Long saleItemId,
        Integer quantity,
        BigDecimal refundAmount,
        Long variantId,
        BigDecimal pricePerUnit,
        BigDecimal taxRate,
        String variantName,
        String sku,
        String productName
) {
}

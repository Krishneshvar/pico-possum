package com.possum.domain.model;

import java.math.BigDecimal;

public record PurchaseOrderItem(
        Long id,
        Long purchaseOrderId,
        Long variantId,
        String variantName,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal unitCost
) {
}

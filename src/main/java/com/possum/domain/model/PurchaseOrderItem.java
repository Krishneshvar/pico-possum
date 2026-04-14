package com.possum.domain.model;

import java.math.BigDecimal;

public record PurchaseOrderItem(
        Long id,
        Long purchaseOrderId,
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal unitCost
) {
}

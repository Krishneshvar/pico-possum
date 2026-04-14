package com.possum.application.returns.dto;

import java.math.BigDecimal;

public record RefundCalculation(
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

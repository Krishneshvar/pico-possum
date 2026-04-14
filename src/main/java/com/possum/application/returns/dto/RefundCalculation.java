package com.possum.application.returns.dto;

import java.math.BigDecimal;

public record RefundCalculation(
        Long saleItemId,
        Integer quantity,
        BigDecimal refundAmount,
        Long variantId
) {
}

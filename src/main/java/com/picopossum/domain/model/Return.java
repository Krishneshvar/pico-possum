package com.picopossum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Return(
        Long id,
        Long saleId,
        String reason,
        LocalDateTime createdAt,
        String invoiceNumber,
        String processedByName,
        BigDecimal totalRefund,
        Long paymentMethodId,
        String paymentMethodName,
        String invoiceId
) {
    public Return {
        if (saleId == null) throw new IllegalArgumentException("Sale ID is required for a return");
        if (totalRefund == null || totalRefund.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total refund cannot be negative");
        }
    }
}

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
}

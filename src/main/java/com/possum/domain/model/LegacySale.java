package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LegacySale(
        String invoiceNumber,
        LocalDateTime saleDate,
        String customerCode,
        String customerName,
        BigDecimal netAmount,
        Long paymentMethodId,
        String paymentMethodName,
        String sourceFile
) {
}

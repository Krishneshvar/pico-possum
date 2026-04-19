package com.picopossum.application.reports.dto;

import java.math.BigDecimal;

public record PaymentMethodStat(
        String paymentMethod,
        int totalTransactions,
        BigDecimal totalAmount
) {
}

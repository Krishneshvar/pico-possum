package com.possum.application.sales.dto;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        long paymentMethodId
) {
    public void validate() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (paymentMethodId <= 0) {
            throw new IllegalArgumentException("Payment method ID must be positive");
        }
    }
}

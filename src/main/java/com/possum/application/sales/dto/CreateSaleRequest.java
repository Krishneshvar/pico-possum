package com.possum.application.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateSaleRequest(
        List<CreateSaleItemRequest> items,
        Long customerId,
        BigDecimal discount,
        List<PaymentRequest> payments
) {
    public void validate() {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }
        if (discount != null && discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        items.forEach(CreateSaleItemRequest::validate);
        if (payments != null) {
            payments.forEach(PaymentRequest::validate);
        }
    }
}

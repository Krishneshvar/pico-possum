package com.possum.application.sales.dto;

import java.math.BigDecimal;

public record UpdateSaleItemRequest(
        long variantId,
        int quantity,
        BigDecimal pricePerUnit,
        BigDecimal discount
) {
    public void validate() {
        if (variantId <= 0) {
            throw new IllegalArgumentException("Variant ID must be positive");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (pricePerUnit != null && pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per unit cannot be negative");
        }
        if (discount != null && discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
    }
}

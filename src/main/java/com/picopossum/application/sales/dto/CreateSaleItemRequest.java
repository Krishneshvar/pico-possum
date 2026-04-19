package com.picopossum.application.sales.dto;

import java.math.BigDecimal;

public record CreateSaleItemRequest(
        long productId,
        int quantity,
        BigDecimal discount,
        BigDecimal pricePerUnit
) {
    public void validate() {
        if (productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (discount != null && discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }
        if (pricePerUnit != null && pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per unit cannot be negative");
        }
    }
}

package com.picopossum.domain.model;

import java.math.BigDecimal;

public record SaleItem(
        Long id,
        Long saleId,
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal pricePerUnit,
        BigDecimal costPerUnit,
        BigDecimal discountAmount,
        Integer returnedQuantity
) {
    public SaleItem {
        if (productId == null) {
            throw new IllegalArgumentException("SaleItem must have a valid productId");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("SaleItem quantity must be greater than zero");
        }
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("SaleItem pricePerUnit cannot be null or negative");
        }
        if (costPerUnit == null || costPerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("SaleItem costPerUnit cannot be null or negative");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("SaleItem discountAmount cannot be null or negative");
        }
    }
}

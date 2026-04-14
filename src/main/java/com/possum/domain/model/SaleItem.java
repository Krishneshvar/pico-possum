package com.possum.domain.model;

import java.math.BigDecimal;

public record SaleItem(
        Long id,
        Long saleId,
        Long variantId,
        String variantName,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal pricePerUnit,
        BigDecimal costPerUnit,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal appliedTaxRate,
        BigDecimal appliedTaxAmount,
        String taxRuleSnapshot,
        BigDecimal discountAmount,
        Integer returnedQuantity
) {
}

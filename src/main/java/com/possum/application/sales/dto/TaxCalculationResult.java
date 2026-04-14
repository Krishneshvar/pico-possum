package com.possum.application.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record TaxCalculationResult(
        List<TaxableItem> items,
        BigDecimal totalTax,
        BigDecimal grandTotal
) {
    public TaxableItem getItemByIndex(int index) {
        return items.get(index);
    }
}

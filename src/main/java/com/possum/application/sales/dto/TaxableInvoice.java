package com.possum.application.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record TaxableInvoice(List<TaxableItem> items) {
    public BigDecimal getSubtotal() {
        return items.stream()
                .map(TaxableItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

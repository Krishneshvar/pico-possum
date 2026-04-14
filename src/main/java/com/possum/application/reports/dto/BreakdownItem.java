package com.possum.application.reports.dto;

import java.math.BigDecimal;

public record BreakdownItem(
        String period,
        String name,
        int totalTransactions,
        BigDecimal cash,
        BigDecimal upi,
        BigDecimal debitCard,
        BigDecimal creditCard,
        BigDecimal giftCard,
        BigDecimal totalSales,
        BigDecimal totalTax,
        BigDecimal totalDiscount,
        BigDecimal refunds
) {
    public BigDecimal getNetSales() {
        BigDecimal gross = totalSales != null ? totalSales : BigDecimal.ZERO;
        BigDecimal desc = totalDiscount != null ? totalDiscount : BigDecimal.ZERO;
        BigDecimal ref = refunds != null ? refunds : BigDecimal.ZERO;
        return gross.subtract(desc).subtract(ref);
    }
}

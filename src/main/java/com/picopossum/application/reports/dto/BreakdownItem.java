package com.picopossum.application.reports.dto;

import java.math.BigDecimal;

public record BreakdownItem(
        String period,
        String name,
        int totalTransactions,
        BigDecimal cash,
        BigDecimal upi,
        BigDecimal card,
        BigDecimal giftCard,
        BigDecimal totalSales,
        BigDecimal totalDiscount,
        BigDecimal refunds,
        int cashCount,
        int upiCount,
        int cardCount,
        int giftCardCount
) {
    public BigDecimal getNetSales() {
        BigDecimal gross = totalSales != null ? totalSales : BigDecimal.ZERO;
        BigDecimal desc = totalDiscount != null ? totalDiscount : BigDecimal.ZERO;
        BigDecimal ref = refunds != null ? refunds : BigDecimal.ZERO;
        return gross.subtract(desc).subtract(ref);
    }
}

package com.possum.application.reports.dto;

import java.math.BigDecimal;

public record SalesReportSummary(
        int totalTransactions,
        BigDecimal totalSales,
        BigDecimal totalTax,
        BigDecimal totalDiscount,
        BigDecimal totalCollected,
        BigDecimal totalRefunds,
        BigDecimal totalCost,
        BigDecimal grossProfit,
        BigDecimal netSales,
        BigDecimal averageSale
) {
}


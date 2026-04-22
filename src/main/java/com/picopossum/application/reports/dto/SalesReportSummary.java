package com.picopossum.application.reports.dto;

import java.math.BigDecimal;

public record SalesReportSummary(
        int totalTransactions,
        BigDecimal totalSales,
        BigDecimal totalDiscount,
        BigDecimal totalTax,
        BigDecimal totalCollected,
        BigDecimal totalRefunds,
        BigDecimal totalCost,
        BigDecimal grossProfit,
        BigDecimal netSales,
        BigDecimal averageSale
) {
    public SalesReportSummary {
        totalSales = totalSales == null ? BigDecimal.ZERO : totalSales;
        totalDiscount = totalDiscount == null ? BigDecimal.ZERO : totalDiscount;
        totalTax = totalTax == null ? BigDecimal.ZERO : totalTax;
        totalCollected = totalCollected == null ? BigDecimal.ZERO : totalCollected;
        totalRefunds = totalRefunds == null ? BigDecimal.ZERO : totalRefunds;
        totalCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        grossProfit = grossProfit == null ? BigDecimal.ZERO : grossProfit;
        netSales = netSales == null ? BigDecimal.ZERO : netSales;
        averageSale = averageSale == null ? BigDecimal.ZERO : averageSale;
    }
}


package com.possum.application.reports.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO for comparing two sales reporting periods.
 */
public record ComparisonReport(
        String periodLabel,
        SalesReportSummary current,
        SalesReportSummary previous
) {
    public BigDecimal salesDelta() {
        return current.totalSales().subtract(previous.totalSales());
    }

    public double salesGrowthPercentage() {
        if (previous.totalSales().compareTo(BigDecimal.ZERO) == 0) {
            return current.totalSales().compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return salesDelta().divide(previous.totalSales(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public BigDecimal profitDelta() {
        return current.grossProfit().subtract(previous.grossProfit());
    }

    public double profitGrowthPercentage() {
        if (previous.grossProfit().compareTo(BigDecimal.ZERO) == 0) {
            return current.grossProfit().compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return profitDelta().divide(previous.grossProfit(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}

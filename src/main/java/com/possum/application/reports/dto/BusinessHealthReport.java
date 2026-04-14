package com.possum.application.reports.dto;

import java.math.BigDecimal;

public record BusinessHealthReport(
        SalesReportSummary salesSummary,
        int lowStockCount,
        int outOfStockCount
) {
}

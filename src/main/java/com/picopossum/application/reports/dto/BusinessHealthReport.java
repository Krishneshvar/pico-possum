package com.picopossum.application.reports.dto;

public record BusinessHealthReport(
        SalesReportSummary salesSummary,
        int lowStockCount,
        int outOfStockCount
) {
}

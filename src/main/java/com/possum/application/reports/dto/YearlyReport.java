package com.possum.application.reports.dto;

import java.time.LocalDate;
import java.util.List;

public record YearlyReport(
        LocalDate startDate,
        LocalDate endDate,
        String reportType,
        SalesReportSummary summary,
        List<BreakdownItem> breakdown
) {
}

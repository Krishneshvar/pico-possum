package com.picopossum.application.reports.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record MultiYearComparisonReport(
    LocalDate startDate,
    LocalDate endDate,
    String interval, // "daily", "weekly", "monthly", "yearly"
    List<YearSeries> series
) {
    public record YearSeries(
        String yearLabel,
        List<DataPoint> dataPoints
    ) {}

    public record DataPoint(
        String label,
        java.math.BigDecimal value
    ) {}
}

package com.possum.application.reports;

import com.possum.application.reports.dto.ComparisonReport;
import com.possum.domain.repositories.ReportsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ReportsServiceTest {

    @Mock
    private ReportsRepository reportsRepository;

    private ReportsService reportsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reportsService = new ReportsService(reportsRepository, null);
    }

    @Test
    void getSalesComparison_returnsPopulatedReport() {
        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 7);
        LocalDate prevStart = LocalDate.of(2025, 5, 25);
        LocalDate prevEnd = LocalDate.of(2025, 5, 31);

        Map<String, Object> currentMap = new HashMap<>();
        currentMap.put("total_sales", new BigDecimal("1000.0"));
        currentMap.put("net_sales", new BigDecimal("1000.0"));
        currentMap.put("gross_profit", new BigDecimal("400.0"));
        
        Map<String, Object> previousMap = new HashMap<>();
        previousMap.put("total_sales", new BigDecimal("800.0"));
        previousMap.put("net_sales", new BigDecimal("800.0"));
        previousMap.put("gross_profit", new BigDecimal("300.0"));

        when(reportsRepository.getSalesReportSummary(eq("2025-06-01"), eq("2025-06-07"), any()))
            .thenReturn(currentMap);
        when(reportsRepository.getSalesReportSummary(eq("2025-05-25"), eq("2025-05-31"), any()))
            .thenReturn(previousMap);

        ComparisonReport report = reportsService.getSalesComparison(start, end, prevStart, prevEnd);

        assertNotNull(report);
        assertEquals(new BigDecimal("1000.0"), report.current().totalSales());
        assertEquals(new BigDecimal("800.0"), report.previous().totalSales());
        assertEquals(25.0, report.salesGrowthPercentage(), 0.01);
    }
}

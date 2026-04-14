package com.possum.application.reports.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonReportTest {

    @Test
    void salesGrowthPercentage_calculatesCorrectly() {
        SalesReportSummary current = createSummary(new BigDecimal("150.0"), BigDecimal.ZERO);
        SalesReportSummary previous = createSummary(new BigDecimal("100.0"), BigDecimal.ZERO);
        
        ComparisonReport report = new ComparisonReport("Last Week", current, previous);
        
        assertEquals(50.0, report.salesGrowthPercentage(), 0.01);
        assertEquals(new BigDecimal("50.0"), report.salesDelta());
    }

    @Test
    void salesGrowthPercentage_handlesDecrease() {
        SalesReportSummary current = createSummary(new BigDecimal("75.0"), BigDecimal.ZERO);
        SalesReportSummary previous = createSummary(new BigDecimal("100.0"), BigDecimal.ZERO);
        
        ComparisonReport report = new ComparisonReport("Last Week", current, previous);
        
        assertEquals(-25.0, report.salesGrowthPercentage(), 0.01);
    }

    @Test
    void salesGrowthPercentage_handlesZeroPrevious() {
        SalesReportSummary current = createSummary(new BigDecimal("100.0"), BigDecimal.ZERO);
        SalesReportSummary previous = createSummary(BigDecimal.ZERO, BigDecimal.ZERO);
        
        ComparisonReport report = new ComparisonReport("Last Week", current, previous);
        
        assertEquals(100.0, report.salesGrowthPercentage());
    }

    @Test
    void profitGrowthPercentage_calculatesCorrectly() {
        SalesReportSummary current = createSummary(BigDecimal.ZERO, new BigDecimal("60.0"));
        SalesReportSummary previous = createSummary(BigDecimal.ZERO, new BigDecimal("40.0"));
        
        ComparisonReport report = new ComparisonReport("Last Week", current, previous);
        
        assertEquals(50.0, report.profitGrowthPercentage(), 0.01);
        assertEquals(new BigDecimal("20.0"), report.profitDelta());
    }

    private SalesReportSummary createSummary(BigDecimal sales, BigDecimal profit) {
        return new SalesReportSummary(
            1, sales, BigDecimal.ZERO, BigDecimal.ZERO, sales, BigDecimal.ZERO, 
            BigDecimal.ZERO, profit, sales, BigDecimal.ZERO
        );
    }
}

package com.possum.domain.repositories;

import java.util.List;
import java.util.Map;

public interface ReportsRepository {
    Map<String, Object> getSalesReportSummary(String startDate, String endDate, List<Long> paymentMethodIds);

    List<Map<String, Object>> getDailyBreakdown(String startDate, String endDate, List<Long> paymentMethodIds);

    List<Map<String, Object>> getMonthlyBreakdown(String startDate, String endDate, List<Long> paymentMethodIds);

    List<Map<String, Object>> getYearlyBreakdown(String startDate, String endDate, List<Long> paymentMethodIds);

    List<Map<String, Object>> getTopSellingProducts(String startDate, String endDate, int limit, List<Long> paymentMethodIds);

    List<Map<String, Object>> getSalesByPaymentMethod(String startDate, String endDate);

    Map<String, Object> getBusinessHealthOverview(String startDate, String endDate);

    List<Map<String, Object>> getStockMovementSummary(String startDate, String endDate, Long categoryId);
}


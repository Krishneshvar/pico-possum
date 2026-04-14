package com.possum.application.reports;

import com.possum.application.reports.dto.*;
import com.possum.domain.model.ProductFlow;
import com.possum.domain.repositories.ProductFlowRepository;
import com.possum.domain.repositories.ReportsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ReportsService {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy");

    private final ReportsRepository reportsRepository;
    private final ProductFlowRepository productFlowRepository;

    public ReportsService(ReportsRepository reportsRepository, ProductFlowRepository productFlowRepository) {
        this.reportsRepository = reportsRepository;
        this.productFlowRepository = productFlowRepository;
    }

    public SalesReportSummary getSalesSummary(LocalDate startDate, LocalDate endDate, List<Long> paymentMethodIds) {
        Map<String, Object> data = reportsRepository.getSalesReportSummary(
                startDate.toString(),
                endDate.toString(),
                paymentMethodIds
        );
        return mapToSummary(data);
    }

    public DailyReport getSalesAnalytics(LocalDate startDate, LocalDate endDate, List<Long> paymentMethodIds) {
        SalesReportSummary summary = getSalesSummary(startDate, endDate, paymentMethodIds);
        List<Map<String, Object>> rawBreakdown = reportsRepository.getDailyBreakdown(
                startDate.toString(),
                endDate.toString(),
                paymentMethodIds
        );
        List<BreakdownItem> breakdown = rawBreakdown.stream()
                .map(item -> mapToBreakdownItem(item, "date", this::formatDate))
                .toList();
        return new DailyReport(startDate, endDate, "daily", summary, breakdown);
    }

    public MonthlyReport getMonthlyReport(LocalDate startDate, LocalDate endDate, List<Long> paymentMethodIds) {
        SalesReportSummary summary = getSalesSummary(startDate, endDate, paymentMethodIds);
        List<Map<String, Object>> rawBreakdown = reportsRepository.getMonthlyBreakdown(
                startDate.toString(),
                endDate.toString(),
                paymentMethodIds
        );
        List<BreakdownItem> breakdown = rawBreakdown.stream()
                .map(item -> mapToBreakdownItem(item, "month", this::formatMonth))
                .toList();
        return new MonthlyReport(startDate, endDate, "monthly", summary, breakdown);
    }

    public YearlyReport getYearlyReport(LocalDate startDate, LocalDate endDate, List<Long> paymentMethodIds) {
        SalesReportSummary summary = getSalesSummary(startDate, endDate, paymentMethodIds);
        List<Map<String, Object>> rawBreakdown = reportsRepository.getYearlyBreakdown(
                startDate.toString(),
                endDate.toString(),
                paymentMethodIds
        );
        List<BreakdownItem> breakdown = rawBreakdown.stream()
                .map(item -> mapToBreakdownItem(item, "year", period -> period))
                .toList();
        return new YearlyReport(startDate, endDate, "yearly", summary, breakdown);
    }

    public List<TopProduct> getTopProducts(LocalDate startDate, LocalDate endDate, int limit, List<Long> paymentMethodIds) {
        List<Map<String, Object>> rawProducts = reportsRepository.getTopSellingProducts(
                startDate.toString(),
                endDate.toString(),
                limit,
                paymentMethodIds
        );
        return rawProducts.stream()
                .map(this::mapToTopProduct)
                .toList();
    }

    public List<PaymentMethodStat> getSalesByPaymentMethod(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rawStats = reportsRepository.getSalesByPaymentMethod(
                startDate.toString(),
                endDate.toString()
        );
        return rawStats.stream()
                .map(this::mapToPaymentMethodStat)
                .toList();
    }

    public BusinessHealthReport getBusinessHealthOverview(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> data = reportsRepository.getBusinessHealthOverview(
                startDate.toString(),
                endDate.toString()
        );
        SalesReportSummary summary = mapToSummary(data);
        int lowStock = (int) data.getOrDefault("low_stock_count", 0);
        int outOfStock = (int) data.getOrDefault("out_of_stock_count", 0);
        return new BusinessHealthReport(summary, lowStock, outOfStock);
    }

    public List<StockMovementStat> getStockMovementSummary(LocalDate startDate, LocalDate endDate, Long categoryId) {
        List<Map<String, Object>> data = reportsRepository.getStockMovementSummary(
                startDate.toString(),
                endDate.toString(),
                categoryId
        );
        return data.stream()
                .map(this::mapToStockMovementStat)
                .toList();
    }


    public ProductFlowReport getProductFlowReport(long variantId, int limit, int offset, 
                                                   String startDate, String endDate, 
                                                   List<String> eventTypes) {
        Map<String, Object> summary = productFlowRepository.getFlowSummary(variantId);
        List<ProductFlow> flows = productFlowRepository.findFlowByVariantId(
                variantId, limit, offset, startDate, endDate, eventTypes
        );
        return new ProductFlowReport(variantId, summary, flows);
    }

    public List<ProductFlow> getInventoryMovement(long variantId, String startDate, String endDate) {
        return productFlowRepository.findFlowByVariantId(variantId, 1000, 0, startDate, endDate, null);
    }

    public ComparisonReport getSalesComparison(LocalDate currentStart, LocalDate currentEnd,
                                              LocalDate previousStart, LocalDate previousEnd) {
        SalesReportSummary current = getSalesSummary(currentStart, currentEnd, null);
        SalesReportSummary previous = getSalesSummary(previousStart, previousEnd, null);
        
        String label = previousStart.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) + 
                       " - " + previousEnd.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        
        return new ComparisonReport(label, current, previous);
    }

    private SalesReportSummary mapToSummary(Map<String, Object> data) {
        int totalTransactions = (int) data.getOrDefault("total_transactions", 0);
        BigDecimal totalSales = (BigDecimal) data.getOrDefault("total_sales", BigDecimal.ZERO);
        BigDecimal totalTax = (BigDecimal) data.getOrDefault("total_tax", BigDecimal.ZERO);
        BigDecimal totalDiscount = (BigDecimal) data.getOrDefault("total_discount", BigDecimal.ZERO);
        BigDecimal totalCollected = (BigDecimal) data.getOrDefault("total_collected", BigDecimal.ZERO);
        BigDecimal totalRefunds = (BigDecimal) data.getOrDefault("total_refunds", BigDecimal.ZERO);
        BigDecimal totalCost = (BigDecimal) data.getOrDefault("total_cost", BigDecimal.ZERO);
        BigDecimal grossProfit = (BigDecimal) data.getOrDefault("gross_profit", BigDecimal.ZERO);
        BigDecimal netSales = (BigDecimal) data.getOrDefault("net_sales", BigDecimal.ZERO);
        BigDecimal averageSale = (BigDecimal) data.getOrDefault("average_sale", BigDecimal.ZERO);
        
        return new SalesReportSummary(
                totalTransactions,
                totalSales,
                totalTax,
                totalDiscount,
                totalCollected,
                totalRefunds,
                totalCost,
                grossProfit,
                netSales,
                averageSale
        );
    }

    private BreakdownItem mapToBreakdownItem(Map<String, Object> item, String periodKey, 
                                             java.util.function.Function<String, String> formatter) {
        String period = (String) item.get(periodKey);
        String name = formatter.apply(period);
        int totalTransactions = (int) item.getOrDefault("total_transactions", 0);
        BigDecimal totalSales = (BigDecimal) item.getOrDefault("total_sales", BigDecimal.ZERO);
        BigDecimal totalTax = (BigDecimal) item.getOrDefault("total_tax", BigDecimal.ZERO);
        BigDecimal totalDiscount = (BigDecimal) item.getOrDefault("total_discount", BigDecimal.ZERO);
        
        BigDecimal cash = (BigDecimal) item.getOrDefault("cash", BigDecimal.ZERO);
        BigDecimal upi = (BigDecimal) item.getOrDefault("upi", BigDecimal.ZERO);
        BigDecimal debitCard = (BigDecimal) item.getOrDefault("debit_card", BigDecimal.ZERO);
        BigDecimal creditCard = (BigDecimal) item.getOrDefault("credit_card", BigDecimal.ZERO);
        BigDecimal giftCard = (BigDecimal) item.getOrDefault("gift_card", BigDecimal.ZERO);
        BigDecimal refunds = (BigDecimal) item.getOrDefault("refunds", BigDecimal.ZERO);

        return new BreakdownItem(period, name, totalTransactions, cash, upi, debitCard, creditCard, giftCard, totalSales, totalTax, totalDiscount, refunds);
    }


    private TopProduct mapToTopProduct(Map<String, Object> item) {
        return new TopProduct(
                ((Number) item.get("product_id")).longValue(),
                (String) item.get("product_name"),
                (String) item.get("variant_name"),
                (String) item.get("sku"),
                (int) item.get("total_quantity_sold"),
                (BigDecimal) item.get("total_revenue")
        );
    }

    private PaymentMethodStat mapToPaymentMethodStat(Map<String, Object> item) {
        return new PaymentMethodStat(
                (String) item.get("payment_method"),
                (int) item.get("total_transactions"),
                (BigDecimal) item.get("total_amount")
        );
    }

    private StockMovementStat mapToStockMovementStat(Map<String, Object> item) {
        return new StockMovementStat(
                (String) item.get("product_name"),
                (String) item.get("variant_name"),
                (String) item.get("sku"),
                (int) item.get("incoming"),
                (int) item.get("outgoing"),
                (int) item.get("returns"),
                (int) item.get("adjustments"),
                (int) item.get("current_stock")
        );
    }

    private String formatDate(String dateStr) {

        LocalDate date = LocalDate.parse(dateStr);
        return com.possum.shared.util.TimeUtil.getDateFormatter().format(date);
    }

    private String formatMonth(String monthStr) {
        LocalDate date = LocalDate.parse(monthStr + "-01");
        return date.format(MONTH_FORMATTER);
    }
}

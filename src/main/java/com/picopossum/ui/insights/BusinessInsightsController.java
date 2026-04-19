package com.picopossum.ui.insights;

import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.reports.dto.ComparisonReport;
import com.picopossum.application.reports.dto.SalesReportSummary;
import com.picopossum.shared.util.CurrencyUtil;
import com.picopossum.ui.common.controls.DateControlUtils;
import com.picopossum.ui.common.controls.NotificationService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class BusinessInsightsController {

    @FXML private DatePicker currentStartDate;
    @FXML private DatePicker currentEndDate;
    @FXML private ComboBox<String> comparisonTypeCombo;

    @FXML private Label salesValueLabel;
    @FXML private Label salesTrendLabel;
    @FXML private FontIcon salesTrendIcon;
    @FXML private HBox salesTrendBox;
    @FXML private Label salesComparisonLabel;

    @FXML private Label profitValueLabel;
    @FXML private Label profitTrendLabel;
    @FXML private FontIcon profitTrendIcon;
    @FXML private HBox profitTrendBox;
    @FXML private Label profitComparisonLabel;

    @FXML private Label marginValueLabel;
    @FXML private Label marginComparisonLabel;

    @FXML private BarChart<String, Number> profitabilityChart;
    @FXML private BarChart<String, Number> comparisonChart;

    private final ReportsService reportsService;

    public BusinessInsightsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @FXML
    public void initialize() {
        setupComparisonTypes();
        setupDefaultDates();
        loadInsights();
    }

    private void setupComparisonTypes() {
        comparisonTypeCombo.setItems(FXCollections.observableArrayList(
                "Previous Period",
                "Last Week",
                "Last Month",
                "Last Year"
        ));
        comparisonTypeCombo.setValue("Previous Period");
    }

    private void setupDefaultDates() {
        DateControlUtils.applyStandardFormat(currentStartDate);
        DateControlUtils.applyStandardFormat(currentEndDate);
        currentStartDate.setValue(LocalDate.now().minusWeeks(1));
        currentEndDate.setValue(LocalDate.now());
    }

    @FXML
    private void handleFilterChange() {
        loadInsights();
    }

    @FXML
    private void handleComparisonChange() {
        loadInsights();
    }

    @FXML
    public void handleRefresh() {
        loadInsights();
        NotificationService.success("Business insights refreshed");
    }

    @FXML
    private void handleReset() {
        setupComparisonTypes();
        setupDefaultDates();
        loadInsights();
    }

    private void loadInsights() {
        try {
            LocalDate start = currentStartDate.getValue();
            LocalDate end = currentEndDate.getValue();
            
            if (start == null || end == null) return;
            
            LocalDate prevStart;
            LocalDate prevEnd;
            
            String compType = comparisonTypeCombo.getValue();
            if (compType == null) compType = "Previous Period";
            
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            
            switch (compType) {
                case "Last Week" -> {
                    prevStart = start.minusWeeks(1);
                    prevEnd = end.minusWeeks(1);
                }
                case "Last Month" -> {
                    prevStart = start.minusMonths(1);
                    prevEnd = end.minusMonths(1);
                }
                case "Last Year" -> {
                    prevStart = start.minusYears(1);
                    prevEnd = end.minusYears(1);
                }
                default -> { // Previous Period
                    prevStart = start.minusDays(days);
                    prevEnd = end.minusDays(days);
                }
            }
            
            ComparisonReport report = reportsService.getSalesComparison(start, end, prevStart, prevEnd);
            updateUI(report);
            
        } catch (Exception e) {
            NotificationService.error("Failed to load insights: " + e.getMessage());
        }
    }

    private void updateUI(ComparisonReport report) {
        // Update KPI Cards
        salesValueLabel.setText(CurrencyUtil.format(report.current().totalSales()));
        updateTrend(salesTrendBox, salesTrendLabel, salesTrendIcon, report.salesGrowthPercentage());
        salesComparisonLabel.setText("vs " + report.periodLabel());

        profitValueLabel.setText(CurrencyUtil.format(report.current().grossProfit()));
        updateTrend(profitTrendBox, profitTrendLabel, profitTrendIcon, report.profitGrowthPercentage());
        profitComparisonLabel.setText("vs " + report.periodLabel());

        BigDecimal totalSales = report.current().totalSales();
        if (totalSales.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margin = report.current().grossProfit()
                    .divide(totalSales, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            marginValueLabel.setText(String.format("%.1f%%", margin.doubleValue()));
        } else {
            marginValueLabel.setText("0.0%");
        }

        // Update Charts
        updateProfitabilityChart(report.current());
        updateComparisonChart(report);
    }

    private void updateTrend(HBox box, Label label, FontIcon icon, double percentage) {
        box.getStyleClass().removeAll("trend-up", "trend-down", "trend-neutral");
        
        if (percentage > 0.1) {
            box.getStyleClass().add("trend-up");
            icon.setIconLiteral("bx-up-arrow-alt");
            label.setText(String.format("+%.1f%%", percentage));
        } else if (percentage < -0.1) {
            box.getStyleClass().add("trend-down");
            icon.setIconLiteral("bx-down-arrow-alt");
            label.setText(String.format("%.1f%%", percentage));
        } else {
            box.getStyleClass().add("trend-neutral");
            icon.setIconLiteral("bx-minus");
            label.setText("0.0%");
        }
    }

    private void updateProfitabilityChart(SalesReportSummary current) {
        XYChart.Series<String, Number> salesSeries = new XYChart.Series<>();
        salesSeries.setName("Revenue");
        salesSeries.getData().add(new XYChart.Data<>("Revenue", current.totalSales()));
        
        XYChart.Series<String, Number> costSeries = new XYChart.Series<>();
        costSeries.setName("Cost");
        costSeries.getData().add(new XYChart.Data<>("Cost", current.totalCost()));
        
        XYChart.Series<String, Number> profitSeries = new XYChart.Series<>();
        profitSeries.setName("Profit");
        profitSeries.getData().add(new XYChart.Data<>("Profit", current.grossProfit()));
        
        profitabilityChart.getData().clear();
        profitabilityChart.getData().addAll(salesSeries, costSeries, profitSeries);

        if (salesSeries.getNode() != null) salesSeries.getNode().getStyleClass().add("profitability-sales-series");
        if (costSeries.getNode() != null) costSeries.getNode().getStyleClass().add("profitability-cost-series");
        if (profitSeries.getNode() != null) profitSeries.getNode().getStyleClass().add("profitability-profit-series");
    }

    private void updateComparisonChart(ComparisonReport report) {
        XYChart.Series<String, Number> currentSeries = new XYChart.Series<>();
        currentSeries.setName("Current Period");
        currentSeries.getData().add(new XYChart.Data<>("Sales", report.current().totalSales()));
        currentSeries.getData().add(new XYChart.Data<>("Profit", report.current().grossProfit()));

        XYChart.Series<String, Number> previousSeries = new XYChart.Series<>();
        previousSeries.setName("Comparison Period");
        previousSeries.getData().add(new XYChart.Data<>("Sales", report.previous().totalSales()));
        previousSeries.getData().add(new XYChart.Data<>("Profit", report.previous().grossProfit()));

        comparisonChart.getData().clear();
        comparisonChart.getData().addAll(currentSeries, previousSeries);

        if (currentSeries.getNode() != null) currentSeries.getNode().getStyleClass().add("comparison-current-series");
        if (previousSeries.getNode() != null) previousSeries.getNode().getStyleClass().add("comparison-previous-series");
    }
}

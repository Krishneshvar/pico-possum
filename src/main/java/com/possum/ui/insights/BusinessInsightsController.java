package com.possum.ui.insights;

import com.possum.application.reports.ReportsService;
import com.possum.application.reports.dto.ComparisonReport;
import com.possum.application.reports.dto.SalesReportSummary;
import com.possum.shared.util.CurrencyUtil;
import com.possum.ui.common.controls.NotificationService;
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
    private void handleRefresh() {
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
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Value breakdown");
        
        series.getData().add(new XYChart.Data<>("Revenue", current.totalSales()));
        series.getData().add(new XYChart.Data<>("Cost", current.totalCost()));
        series.getData().add(new XYChart.Data<>("Profit", current.grossProfit()));
        
        profitabilityChart.getData().clear();
        profitabilityChart.getData().add(series);
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
        comparisonChart.getData().add(currentSeries);
        comparisonChart.getData().add(previousSeries);
    }
}

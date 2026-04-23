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
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import com.picopossum.application.reports.dto.MultiYearComparisonReport;
import java.util.ArrayList;
import javafx.application.Platform;
import java.util.List;
import java.util.stream.Collectors;

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

    @FXML private LineChart<String, Number> multiYearComparisonChart;
    @FXML private Spinner<Integer> previousYearsSpinner;
    @FXML private ComboBox<String> periodIntervalCombo;

    private final ReportsService reportsService;

    public BusinessInsightsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @FXML
    public void initialize() {
        setupComparisonTypes();
        setupDefaultDates();
        setupMultiYearControls();
        loadInsights();
    }

    private void setupMultiYearControls() {
        previousYearsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 3));
        previousYearsSpinner.valueProperty().addListener((obs, oldVal, newVal) -> loadMultiYearData());

        periodIntervalCombo.setItems(FXCollections.observableArrayList("Daily", "Weekly", "Monthly", "Yearly"));
        periodIntervalCombo.setValue("Weekly");
        periodIntervalCombo.setOnAction(e -> loadMultiYearData());
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
        LocalDate start = currentStartDate.getValue();
        LocalDate end = currentEndDate.getValue();
        
        if (start == null || end == null) return;
        
        String compType = comparisonTypeCombo.getValue();
        if (compType == null) compType = "Previous Period";
        
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        
        final LocalDate prevStart;
        final LocalDate prevEnd;
        
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

        javafx.concurrent.Task<ComparisonReport> task = new javafx.concurrent.Task<>() {
            @Override
            protected ComparisonReport call() throws Exception {
                return reportsService.getSalesComparison(start, end, prevStart, prevEnd);
            }
        };

        task.setOnSucceeded(e -> updateUI(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load insights", ex);
            NotificationService.error("Failed to load insights: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();

        loadMultiYearData();
    }

    private void loadMultiYearData() {
        LocalDate start = currentStartDate.getValue();
        LocalDate end = currentEndDate.getValue();
        if (start == null || end == null) return;

        int prevYears = previousYearsSpinner.getValue();
        String interval = periodIntervalCombo.getValue();

        javafx.concurrent.Task<MultiYearComparisonReport> multiYearTask = new javafx.concurrent.Task<>() {
            @Override
            protected MultiYearComparisonReport call() throws Exception {
                return reportsService.getMultiYearComparison(start, end, prevYears, interval);
            }
        };

        multiYearTask.setOnSucceeded(e -> updateMultiYearChart(multiYearTask.getValue()));
        multiYearTask.setOnFailed(e -> {
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load multi-year data", multiYearTask.getException());
        });

        Thread t = new Thread(multiYearTask);
        t.setDaemon(true);
        t.start();
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

    private static final String[] CHART_COLORS = {
        "#3B82F6", // Blue (Current)
        "#10B981", // Emerald
        "#F59E0B", // Amber
        "#EF4444", // Red
        "#8B5CF6", // Violet
        "#EC4899", // Pink
        "#06B6D4", // Cyan
        "#F97316", // Orange
        "#6366F1", // Indigo
        "#84CC16", // Lime
        "#14B8A6"  // Teal
    };

    private void updateMultiYearChart(MultiYearComparisonReport report) {
        multiYearComparisonChart.getData().clear();
        
        for (int i = 0; i < report.series().size(); i++) {
            MultiYearComparisonReport.YearSeries yearSeries = report.series().get(i);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(yearSeries.yearLabel());
            
            for (MultiYearComparisonReport.DataPoint dp : yearSeries.dataPoints()) {
                series.getData().add(new XYChart.Data<>(dp.label(), dp.value()));
            }
            
            multiYearComparisonChart.getData().add(series);
            
            // Apply distinct colors
            final String color = CHART_COLORS[i % CHART_COLORS.length];
            if (series.getNode() != null) {
                series.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2.5px;");
            }
            
            // Apply color to legend symbols if they exist yet
            Platform.runLater(() -> {
                for (javafx.scene.Node n : multiYearComparisonChart.lookupAll(".chart-legend-item-symbol")) {
                    // This is a bit hacky in JavaFX but works for styling legend
                    if (n.getParent() instanceof Label label && label.getText().equals(yearSeries.yearLabel())) {
                        n.setStyle("-fx-background-color: " + color + ";");
                    }
                }
            });
        }
    }
}

package com.picopossum.ui.reports;

import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.sales.SalesService;
import com.picopossum.application.reports.dto.*;
import com.picopossum.domain.model.PaymentMethod;
import com.picopossum.ui.common.controls.DateControlUtils;
import com.picopossum.ui.common.controls.NotificationService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.scene.layout.VBox;
import javafx.scene.Node;

import com.picopossum.shared.util.CurrencyUtil;
import java.time.LocalDate;
import java.util.List;
import com.picopossum.ui.common.lifecycle.Disposable;

public class SalesAnalyticsController implements Disposable {
    
    @FXML private javafx.scene.control.DatePicker startDatePicker;
    @FXML private javafx.scene.control.DatePicker endDatePicker;
    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private javafx.scene.layout.VBox paymentMethodContainer;
    private com.picopossum.ui.common.controls.MultiSelectFilter<PaymentMethod> paymentMethodFilter;
    @FXML private Label totalSalesLabel;
    @FXML private Label transactionsLabel;
    @FXML private Label avgSaleLabel;
    @FXML private Label totalDiscountLabel;
    @FXML private BarChart<String, Number> topProductsChart;
    @FXML private LineChart<String, Number> salesTrendChart;
    @FXML private PieChart paymentMethodsChart;
    
    private ReportsService reportsService;
    private SalesService salesService;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean disposed = false;
    
    // Tooltip elements
    private Popup tooltip;
    private Label tooltipTitle;
    private Label tooltipValue;

    public SalesAnalyticsController(ReportsService reportsService, SalesService salesService) {
        this.reportsService = reportsService;
        this.salesService = salesService;
    }

    @FXML
    public void initialize() {
        setupReportTypes();
        setupPaymentMethods();
        setupDatePickers();
        setupTooltip();
        loadReports();
    }

    private void setupDatePickers() {
        DateControlUtils.applyStandardFormat(startDatePicker);
        DateControlUtils.applyStandardFormat(endDatePicker);

        // Default range: This month
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
        
        startDate = startDatePicker.getValue();
        endDate = endDatePicker.getValue();
    }

    private void setupReportTypes() {
        reportTypeCombo.setItems(FXCollections.observableArrayList(
            "Daily", "Monthly", "Yearly"
        ));
        reportTypeCombo.setValue("Daily");
    }

    private void setupTooltip() {
        tooltip = new Popup();
        VBox card = new VBox(4);
        card.getStyleClass().add("chart-tooltip-card");
        
        tooltipTitle = new Label();
        tooltipTitle.getStyleClass().add("chart-tooltip-title");
        
        tooltipValue = new Label();
        tooltipValue.getStyleClass().add("chart-tooltip-value");
        
        card.getChildren().addAll(tooltipTitle, tooltipValue);
        tooltip.getContent().add(card);
        tooltip.setAutoHide(false);
    }

    private void setupPaymentMethods() {
        try {
            List<PaymentMethod> methods = salesService.getPaymentMethods();
            paymentMethodFilter = new com.picopossum.ui.common.controls.MultiSelectFilter<>(
                "All Methods",
                PaymentMethod::name
            );
            paymentMethodFilter.setItems(methods);
            paymentMethodFilter.setPrefWidth(180);
            paymentMethodFilter.getSelectedItems().addListener((javafx.collections.ListChangeListener<PaymentMethod>) c -> loadReports());
            
            paymentMethodContainer.getChildren().add(paymentMethodFilter);
        } catch (Exception e) {
            NotificationService.error("Failed to load payment methods");
        }
    }





    @FXML
    private void handleDateChange() {
        startDate = startDatePicker.getValue();
        endDate = endDatePicker.getValue();
        loadReports();
    }

    @FXML
    private void handleReset() {
        reportTypeCombo.setValue("Daily");
        if (paymentMethodFilter != null) {
            paymentMethodFilter.clearSelection();
        }
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
        startDate = startDatePicker.getValue();
        endDate = endDatePicker.getValue();
        loadReports();
    }

    @FXML
    private void handleReportTypeChange() {
        loadReports();
    }



    @FXML
    public void handleRefresh() {
        loadReports();
        NotificationService.success("Sales analytics refreshed");
    }



    private void loadReports() {
        List<Long> paymentMethodIds = getSelectedPaymentMethodIds();
        String type = reportTypeCombo.getValue();
        if (type == null) type = "Daily";
        final String reportType = type;

        javafx.concurrent.Task<AnalyticsBundle> task = new javafx.concurrent.Task<>() {
            @Override
            protected AnalyticsBundle call() throws Exception {
                SalesReportSummary summary = reportsService.getSalesSummary(startDate, endDate, paymentMethodIds);
                List<TopProduct> topProducts = reportsService.getTopProducts(startDate, endDate, 10, paymentMethodIds);
                
                List<? extends BreakdownItem> breakdown;
                if ("Monthly".equals(reportType)) {
                    breakdown = reportsService.getMonthlyReport(startDate, endDate, paymentMethodIds).breakdown();
                } else if ("Yearly".equals(reportType)) {
                    breakdown = reportsService.getYearlyReport(startDate, endDate, paymentMethodIds).breakdown();
                } else {
                    breakdown = reportsService.getSalesAnalytics(startDate, endDate, paymentMethodIds).breakdown();
                }

                List<PaymentMethodStat> paymentStats = reportsService.getSalesByPaymentMethod(startDate, endDate);
                
                return new AnalyticsBundle(summary, topProducts, breakdown, paymentStats);
            }
        };

        task.setOnSucceeded(e -> {
            if (disposed) return;
            AnalyticsBundle bundle = task.getValue();
            updateSummaryUI(bundle.summary());
            updateTopProductsChart(bundle.topProducts());
            updateSalesTrendChart(bundle.breakdown(), reportType);
            updatePaymentMethodChart(bundle.paymentStats());
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load analytics", ex);
            NotificationService.error("Failed to load analytics: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        new Thread(task).start();
    }

    private record AnalyticsBundle(
        SalesReportSummary summary,
        List<TopProduct> topProducts,
        List<? extends BreakdownItem> breakdown,
        List<PaymentMethodStat> paymentStats
    ) {}

    private void updateSummaryUI(SalesReportSummary summary) {
        totalSalesLabel.setText(CurrencyUtil.format(summary.totalSales()));
        transactionsLabel.setText(String.valueOf(summary.totalTransactions()));
        avgSaleLabel.setText(CurrencyUtil.format(summary.averageSale()));
        totalDiscountLabel.setText(CurrencyUtil.format(summary.totalDiscount()));
    }

    private List<Long> getSelectedPaymentMethodIds() {
        if (paymentMethodFilter == null) return null;
        List<PaymentMethod> selected = paymentMethodFilter.getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        return selected.stream().map(PaymentMethod::id).toList();
    }
    private void updateTopProductsChart(List<TopProduct> topProducts) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Quantity Sold");
        for (TopProduct product : topProducts) {
            String label = product.productName();
            if (label.length() > 20) label = label.substring(0, 20) + "...";
            series.getData().add(new XYChart.Data<>(label, product.totalQuantitySold()));
        }
        topProductsChart.getData().clear();
        topProductsChart.getData().add(series);
    }

    private void updateSalesTrendChart(List<? extends BreakdownItem> breakdown, String type) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Sales");
        for (BreakdownItem item : breakdown) {
            series.getData().add(new XYChart.Data<>(item.name(), item.totalSales()));
        }
        salesTrendChart.getData().clear();
        salesTrendChart.getData().add(series);
        salesTrendChart.setTitle(type + " Sales Trend");

        for (XYChart.Data<String, Number> data : series.getData()) {
            Node node = data.getNode();
            if (node != null) {
                node.setOnMouseEntered(e -> {
                    tooltipTitle.setText(data.getXValue());
                    tooltipValue.setText(CurrencyUtil.format(new java.math.BigDecimal(data.getYValue().toString())));
                    tooltip.show(node, e.getScreenX() + 15, e.getScreenY() - 40);
                    node.setScaleX(1.5); node.setScaleY(1.5);
                });
                node.setOnMouseExited(e -> {
                    tooltip.hide();
                    node.setScaleX(1.0); node.setScaleY(1.0);
                });
            }
        }
    }

    private void updatePaymentMethodChart(List<PaymentMethodStat> stats) {
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (PaymentMethodStat stat : stats) {
            pieData.add(new PieChart.Data(stat.paymentMethod(), stat.totalAmount().doubleValue()));
        }
        paymentMethodsChart.setData(pieData);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (tooltip != null) {
            tooltip.hide();
        }
    }
}

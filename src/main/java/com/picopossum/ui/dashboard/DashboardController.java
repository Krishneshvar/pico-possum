package com.picopossum.ui.dashboard;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.reports.dto.SalesReportSummary;
import com.picopossum.application.reports.dto.TopProduct;
import com.picopossum.domain.model.Product;
import com.picopossum.ui.common.controls.DataTableView;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import com.picopossum.shared.util.CurrencyUtil;
import java.time.LocalDate;
import java.util.List;

public class DashboardController {
    
    @FXML private Label dailySalesLabel;
    @FXML private Label transactionsLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label backupStatusLabel;
    @FXML private javafx.scene.layout.HBox backupBadge;
    @FXML private javafx.scene.layout.VBox salesStatCard;
    @FXML private javafx.scene.layout.VBox transStatCard;
    @FXML private Label atvLabel;
    @FXML private javafx.scene.layout.VBox atvStatCard;
    @FXML private javafx.scene.layout.VBox stockStatCard;
    
    @FXML private DataTableView<TopProduct> topProductsTable;
    @FXML private DataTableView<Product> lowStockTable;
    @FXML private javafx.scene.chart.LineChart<String, Number> salesTrendChart;
    @FXML private javafx.scene.chart.CategoryAxis hourAxis;
    
    private ReportsService reportsService;
    private InventoryService inventoryService;
    private com.picopossum.infrastructure.backup.DatabaseBackupService backupService;

    public DashboardController(ReportsService reportsService, InventoryService inventoryService, 
                               com.picopossum.infrastructure.backup.DatabaseBackupService backupService) {
        this.reportsService = reportsService;
        this.inventoryService = inventoryService;
        this.backupService = backupService;
    }

    @FXML
    public void initialize() {
        setupTopProductsTable();
        setupLowStockTable();
        loadDashboardData();
    }

    private void setupTopProductsTable() {
        TableColumn<TopProduct, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        
        TableColumn<TopProduct, Integer> qtyCol = new TableColumn<>("Qty Sold");
        qtyCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().totalQuantitySold()));
        
        TableColumn<TopProduct, BigDecimal> revenueCol = new TableColumn<>("Revenue");
        revenueCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().totalRevenue()));
        revenueCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyUtil.format(item));
            }
        });
        topProductsTable.getTableView().getColumns().setAll(List.of(nameCol, qtyCol, revenueCol));
        topProductsTable.setEmptyMessage("No data available");
        topProductsTable.setEmptySubtitle("Top selling products will appear here.");
    }

    private void setupLowStockTable() {
        TableColumn<Product, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Product, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        
        TableColumn<Product, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stock()));
        
        TableColumn<Product, Integer> alertCol = new TableColumn<>("Alert Level");
        alertCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stockAlertCap()));
        lowStockTable.getTableView().getColumns().setAll(List.of(nameCol, skuCol, stockCol, alertCol));
        lowStockTable.setEmptyMessage("Inventory healthy");
        lowStockTable.setEmptySubtitle("No products are currently low on stock.");
    }

    private record DashboardBundle(
            SalesReportSummary summary,
            List<TopProduct> topProducts,
            List<Product> lowStockProducts,
            List<com.picopossum.application.reports.dto.BreakdownItem> hourlyData,
            String backupStatus
    ) {}

    private void loadDashboardData() {
        setLoading(true);
        LocalDate today = LocalDate.now();

        javafx.concurrent.Task<DashboardBundle> task = new javafx.concurrent.Task<>() {
            @Override
            protected DashboardBundle call() throws Exception {
                SalesReportSummary summary = reportsService.getSalesSummary(today, today, null);
                List<TopProduct> topProducts = reportsService.getTopProducts(today, today, 8, null);
                List<Product> lowStockProducts = inventoryService.getLowStockAlerts();
                List<com.picopossum.application.reports.dto.BreakdownItem> hourlyData = reportsService.getHourlyAnalytics(today, null);
                
                String backupStatus = "Backup Pending";
                if (backupService != null) {
                    java.util.Optional<java.nio.file.Path> latest = backupService.findLatestBackup();
                    if (latest.isPresent()) {
                         java.nio.file.attribute.FileTime modified = java.nio.file.Files.getLastModifiedTime(latest.get());
                         java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(modified.toInstant(), java.time.ZoneId.systemDefault());
                         java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, hh:mm a");
                         backupStatus = "Last backup: " + ldt.format(formatter);
                    }
                }
                
                return new DashboardBundle(summary, topProducts, lowStockProducts, hourlyData, backupStatus);
            }
        };

        task.setOnSucceeded(e -> {
            DashboardBundle bundle = task.getValue();
            updateUI(bundle);
            setLoading(false);
        });

        task.setOnFailed(e -> {
            setLoading(false);
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Dashboard load failed", task.getException());
            com.picopossum.ui.common.controls.NotificationService.error("Failed to refresh dashboard");
        });

        new Thread(task).start();
    }

    private void updateUI(DashboardBundle bundle) {
        dailySalesLabel.setText(CurrencyUtil.format(bundle.summary().totalSales()));
        transactionsLabel.setText(String.valueOf(bundle.summary().totalTransactions()));
        atvLabel.setText(CurrencyUtil.format(bundle.summary().averageSale()));
        
        topProductsTable.setItems(FXCollections.observableArrayList(bundle.topProducts()));
        
        lowStockLabel.setText(String.valueOf(bundle.lowStockProducts().size()));
        lowStockTable.setItems(FXCollections.observableArrayList(bundle.lowStockProducts()));

        backupStatusLabel.setText(bundle.backupStatus());
        updateTrendChart(bundle.hourlyData());
        updateStatCardStates(bundle.lowStockProducts().size());
    }

    private void updateTrendChart(List<com.picopossum.application.reports.dto.BreakdownItem> hourlyData) {
        salesTrendChart.getData().clear();
        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        series.setName("Revenue");

        // Ensure all hours are represented for a nice chart (Typical business hours 9 AM - 9 PM)
        for (int h = 9; h <= 21; h++) {
            String hourStr = String.format("%02d:00", h);
            BigDecimal revenue = hourlyData.stream()
                .filter(item -> item.name().equals(hourStr))
                .map(item -> item.totalSales())
                .findFirst()
                .orElse(BigDecimal.ZERO);
            series.getData().add(new javafx.scene.chart.XYChart.Data<>(hourStr, revenue));
        }

        salesTrendChart.getData().add(series);
    }

    private void updateStatCardStates(int lowStockCount) {
        stockStatCard.getStyleClass().remove("critical");
        if (lowStockCount > 0) {
            stockStatCard.getStyleClass().add("critical");
        }
    }

    private void setLoading(boolean loading) {
        // Can add more UI feedback here if needed
        if (loading) {
            topProductsTable.setLoading(true);
            lowStockTable.setLoading(true);
        } else {
            topProductsTable.setLoading(false);
            lowStockTable.setLoading(false);
        }
    }

    public void refresh() {
        loadDashboardData();
    }

    @FXML
    public void handleRefresh() {
        refresh();
        com.picopossum.ui.common.controls.NotificationService.success("Dashboard data refreshed");
    }
}

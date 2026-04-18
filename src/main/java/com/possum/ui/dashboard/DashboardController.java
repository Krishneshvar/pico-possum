package com.possum.ui.dashboard;

import com.possum.application.inventory.InventoryService;
import com.possum.application.reports.ReportsService;
import com.possum.application.reports.dto.SalesReportSummary;
import com.possum.application.reports.dto.TopProduct;
import com.possum.domain.model.Product;
import com.possum.ui.common.controls.DataTableView;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import com.possum.shared.util.CurrencyUtil;
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
    @FXML private javafx.scene.layout.VBox stockStatCard;
    
    @FXML private DataTableView<TopProduct> topProductsTable;
    @FXML private DataTableView<Product> lowStockTable;
    @FXML private javafx.scene.chart.LineChart<String, Number> salesTrendChart;
    @FXML private javafx.scene.chart.CategoryAxis hourAxis;
    
    private ReportsService reportsService;
    private InventoryService inventoryService;
    private com.possum.infrastructure.backup.DatabaseBackupService backupService;

    public DashboardController(ReportsService reportsService, InventoryService inventoryService, 
                               com.possum.infrastructure.backup.DatabaseBackupService backupService) {
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

    private void loadDashboardData() {
        LocalDate today = LocalDate.now();
        
        SalesReportSummary summary = reportsService.getSalesSummary(today, today, null);
        dailySalesLabel.setText(CurrencyUtil.format(summary.totalSales()));
        transactionsLabel.setText(String.valueOf(summary.totalTransactions()));
        
        List<TopProduct> topProducts = reportsService.getTopProducts(today, today, 10, null);
        topProductsTable.setItems(FXCollections.observableArrayList(topProducts));
        
        List<Product> lowStockProducts = inventoryService.getLowStockAlerts();
        lowStockLabel.setText(String.valueOf(lowStockProducts.size()));
        lowStockTable.setItems(FXCollections.observableArrayList(lowStockProducts));

        updateBackupStatus();
        updateTrendChart();
        updateStatCardStates(lowStockProducts.size());
    }

    private void updateTrendChart() {
        salesTrendChart.getData().clear();
        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        series.setName("Revenue");

        List<com.possum.application.reports.dto.BreakdownItem> hourlyData = reportsService.getHourlyAnalytics(LocalDate.now(), null);
        
        // Ensure all hours are represented for a nice chart
        for (int h = 9; h <= 21; h++) { // Typical business hours 9 AM - 9 PM
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

    private void updateBackupStatus() {
        if (backupService == null) return;
        
        java.util.Optional<java.nio.file.Path> latest = backupService.findLatestBackup();
        if (latest.isPresent()) {
            try {
                java.nio.file.attribute.FileTime modified = java.nio.file.Files.getLastModifiedTime(latest.get());
                java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(modified.toInstant(), java.time.ZoneId.systemDefault());
                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, hh:mm a");
                backupStatusLabel.setText("Last backup: " + ldt.format(formatter));
            } catch (java.io.IOException e) {
                backupStatusLabel.setText("System Protected");
            }
        } else {
            backupStatusLabel.setText("Backup Pending");
        }
    }

    @FXML
    public void handleRefresh() {
        loadDashboardData();
        com.possum.ui.common.controls.NotificationService.success("Dashboard data refreshed");
    }
}

package com.possum.ui.dashboard;

import com.possum.application.inventory.InventoryService;
import com.possum.application.reports.ReportsService;
import com.possum.application.reports.dto.SalesReportSummary;
import com.possum.application.reports.dto.TopProduct;
import com.possum.domain.model.Variant;
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
    @FXML private DataTableView<TopProduct> topProductsTable;
    @FXML private DataTableView<Variant> lowStockTable;
    
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
        
        TableColumn<TopProduct, String> variantCol = new TableColumn<>("Variant");
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().variantName()));
        
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
        topProductsTable.getTableView().getColumns().setAll(List.of(nameCol, variantCol, qtyCol, revenueCol));
        topProductsTable.setEmptyMessage("No data available");
        topProductsTable.setEmptySubtitle("Top selling products will appear here.");
    }

    private void setupLowStockTable() {
        TableColumn<Variant, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        
        TableColumn<Variant, String> variantCol = new TableColumn<>("Variant");
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Variant, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        
        TableColumn<Variant, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stock()));
        
        TableColumn<Variant, Integer> alertCol = new TableColumn<>("Alert Level");
        alertCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stockAlertCap()));
        lowStockTable.getTableView().getColumns().setAll(List.of(nameCol, variantCol, skuCol, stockCol, alertCol));
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
        
        List<Variant> lowStockVariants = inventoryService.getLowStockAlerts();
        lowStockLabel.setText(String.valueOf(lowStockVariants.size()));
        lowStockTable.setItems(FXCollections.observableArrayList(lowStockVariants));

        updateBackupStatus();
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

    public void refresh() {
        loadDashboardData();
    }
}

package com.possum.ui.reports;

import com.possum.application.reports.ReportsService;
import com.possum.application.reports.dto.BreakdownItem;
import com.possum.application.sales.SalesService;
import com.possum.ui.common.controls.DataTableView;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class SalesReportsController {

    @FXML private ComboBox<String> reportTypeCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> amountFilterColCombo;
    @FXML private TextField minAmountField;
    @FXML private TextField maxAmountField;
    @FXML private DataTableView<BreakdownItem> breakdownTable;
    
    @FXML private VBox columnFilterContainer;

    @FXML private Label totalLabel;
    @FXML private Label totalTransactionsLabel;
    @FXML private Label totalCashLabel;
    @FXML private Label totalUpiLabel;
    @FXML private Label totalDebitLabel;
    @FXML private Label totalCreditLabel;
    @FXML private Label totalGiftLabel;
    @FXML private Label totalSalesLabel;
    @FXML private Label totalRefundsLabel;
    @FXML private Label totalNetSalesLabel;

    private final ReportsService reportsService;

    private SalesReportTableManager tableManager;
    private SalesReportExporter exporter;

    public SalesReportsController(ReportsService reportsService, SalesService salesService) {
        this.reportsService = reportsService;
    }

    @FXML
    public void handleExportCsv() {
        exporter.exportData(".csv", breakdownTable.getTableView().getItems());
    }

    @FXML
    public void handleExportExcel() {
        exporter.exportData(".xlsx", breakdownTable.getTableView().getItems());
    }

    @FXML
    public void initialize() {
        List<Label> totalLabels = List.of(
            totalLabel, totalTransactionsLabel, totalCashLabel, totalUpiLabel, totalDebitLabel,
            totalCreditLabel, totalGiftLabel, totalSalesLabel, totalRefundsLabel, totalNetSalesLabel
        );

        tableManager = new SalesReportTableManager(breakdownTable, totalLabels);
        tableManager.setupTable();
        tableManager.setupColumnFilter(columnFilterContainer, totalLabels);
        
        setupFilters();
        bindTotalsAlignment();

        exporter = new SalesReportExporter(
            breakdownTable.getScene() != null ? breakdownTable.getScene().getWindow() : null,
            tableManager::calculateDynamicGrossSales,
            tableManager::calculateDynamicNetSales,
            tableManager::isColumnVisible
        );

        handleRefresh();
    }

    private void bindTotalsAlignment() {
        totalTransactionsLabel.setAlignment(Pos.CENTER);
        totalCashLabel.setAlignment(Pos.CENTER_RIGHT);
        totalUpiLabel.setAlignment(Pos.CENTER_RIGHT);
        totalDebitLabel.setAlignment(Pos.CENTER_RIGHT);
        totalCreditLabel.setAlignment(Pos.CENTER_RIGHT);
        totalGiftLabel.setAlignment(Pos.CENTER_RIGHT);
        totalSalesLabel.setAlignment(Pos.CENTER_RIGHT);
        totalRefundsLabel.setAlignment(Pos.CENTER_RIGHT);
        totalNetSalesLabel.setAlignment(Pos.CENTER_RIGHT);
    }

    @FXML
    public void handleRefresh() {
        loadData();
    }

    @FXML
    public void handleReportTypeChange() {
        loadData();
    }

    @FXML
    public void handleDateChange() {
        loadData();
    }

    @FXML
    public void handleReset() {
        reportTypeCombo.setValue("Daily");
        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());
        amountFilterColCombo.setValue("None");
        minAmountField.clear();
        maxAmountField.clear();
        loadData();
    }

    private void loadData() {
        String reportType = reportTypeCombo.getValue().toLowerCase();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null || endDate == null) return;

        List<BreakdownItem> breakdown;
        if ("daily".equals(reportType)) {
            breakdown = reportsService.getSalesAnalytics(startDate, endDate, null).breakdown();
        } else if ("monthly".equals(reportType)) {
            breakdown = reportsService.getMonthlyReport(startDate, endDate, null).breakdown();
        } else {
            breakdown = reportsService.getYearlyReport(startDate, endDate, null).breakdown();
        }

        String filteredCol = amountFilterColCombo.getValue();
        if (filteredCol != null && !"None".equals(filteredCol)) {
            final BigDecimal min = parseAmount(minAmountField.getText(), null);
            final BigDecimal max = parseAmount(maxAmountField.getText(), null);

            breakdown = breakdown.stream()
                .filter(item -> {
                    BigDecimal val = getFieldValue(item, filteredCol);
                    if (val == null) val = BigDecimal.ZERO;
                    
                    boolean matchesMin = (min == null || val.compareTo(min) >= 0);
                    boolean matchesMax = (max == null || val.compareTo(max) <= 0);
                    
                    return matchesMin && matchesMax;
                })
                .collect(Collectors.toList());
        }

        breakdownTable.getTableView().setItems(FXCollections.observableArrayList(breakdown));
        tableManager.updateTotals();
    }

    private void setupFilters() {
        reportTypeCombo.setItems(FXCollections.observableArrayList("Daily", "Monthly", "Yearly"));
        reportTypeCombo.setValue("Daily");

        startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        endDatePicker.setValue(LocalDate.now());

        amountFilterColCombo.setItems(FXCollections.observableArrayList(
            "None", "Transactions", "Cash", "UPI", "Debit Card", "Credit Card", "Gift Card", "Gross Sales", "Refunds", "Net Sales"
        ));
        amountFilterColCombo.setValue("None");

        minAmountField.textProperty().addListener((obs, oldVal, newVal) -> handleRefresh());
        maxAmountField.textProperty().addListener((obs, oldVal, newVal) -> handleRefresh());
        amountFilterColCombo.setOnAction(e -> handleRefresh());
    }

    private BigDecimal parseAmount(String text, BigDecimal defaultValue) {
        if (text == null || text.trim().isEmpty()) return defaultValue;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal getFieldValue(BreakdownItem item, String column) {
        return switch (column) {
            case "Transactions" -> BigDecimal.valueOf(item.totalTransactions());
            case "Cash" -> item.cash();
            case "UPI" -> item.upi();
            case "Debit Card" -> item.debitCard();
            case "Credit Card" -> item.creditCard();
            case "Gift Card" -> item.giftCard();
            case "Gross Sales" -> item.totalSales();
            case "Refunds" -> item.refunds();
            case "Net Sales" -> item.getNetSales();
            default -> BigDecimal.ZERO;
        };
    }
}

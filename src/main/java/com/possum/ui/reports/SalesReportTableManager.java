package com.possum.ui.reports;

import com.possum.application.reports.dto.BreakdownItem;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.MultiSelectFilter;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;

public class SalesReportTableManager {

    private final DataTableView<BreakdownItem> breakdownTable;
    
    private TableColumn<BreakdownItem, String> periodCol;
    private TableColumn<BreakdownItem, Integer> transactionsCol;
    private TableColumn<BreakdownItem, BigDecimal> cashCol;
    private TableColumn<BreakdownItem, BigDecimal> upiCol;
    private TableColumn<BreakdownItem, BigDecimal> debitCardCol;
    private TableColumn<BreakdownItem, BigDecimal> creditCardCol;
    private TableColumn<BreakdownItem, BigDecimal> giftCardCol;
    private TableColumn<BreakdownItem, BigDecimal> salesCol;
    private TableColumn<BreakdownItem, BigDecimal> refundsCol;
    private TableColumn<BreakdownItem, BigDecimal> netSalesCol;

    private final List<Label> totalLabels;

    public SalesReportTableManager(DataTableView<BreakdownItem> breakdownTable, 
                                   List<Label> totalLabels) {
        this.breakdownTable = breakdownTable;
        this.totalLabels = totalLabels;
    }

    public void setupTable() {
        periodCol = new TableColumn<>("Date");
        transactionsCol = new TableColumn<>("Transactions");
        cashCol = new TableColumn<>("Cash");
        upiCol = new TableColumn<>("UPI");
        debitCardCol = new TableColumn<>("Debit Card");
        creditCardCol = new TableColumn<>("Credit Card");
        giftCardCol = new TableColumn<>("Gift Card");
        salesCol = new TableColumn<>("Gross Sales");
        refundsCol = new TableColumn<>("Refunds");
        netSalesCol = new TableColumn<>("Net Sales");

        breakdownTable.getTableView().getColumns().clear();
        breakdownTable.getTableView().getColumns().addAll(List.of(
            periodCol, transactionsCol, cashCol, upiCol, debitCardCol, 
            creditCardCol, giftCardCol, salesCol, refundsCol, netSalesCol
        ));
        
        breakdownTable.setEmptyMessage("No analytics records found");
        breakdownTable.setEmptySubtitle("Try adjusting your date range or filter criteria.");

        periodCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        transactionsCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().totalTransactions()));
        cashCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().cash()));
        upiCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().upi()));
        debitCardCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().debitCard()));
        creditCardCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().creditCard()));
        giftCardCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().giftCard()));
        salesCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(calculateDynamicGrossSales(cellData.getValue())));
        refundsCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().refunds()));
        netSalesCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(calculateDynamicNetSales(cellData.getValue())));
        
        Callback<TableColumn<BreakdownItem, BigDecimal>, TableCell<BreakdownItem, BigDecimal>> currencyCellFactory = column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : CurrencyUtil.format(item));
            }
        };

        cashCol.setCellFactory(currencyCellFactory);
        upiCol.setCellFactory(currencyCellFactory);
        debitCardCol.setCellFactory(currencyCellFactory);
        creditCardCol.setCellFactory(currencyCellFactory);
        giftCardCol.setCellFactory(currencyCellFactory);
        salesCol.setCellFactory(currencyCellFactory);
        refundsCol.setCellFactory(currencyCellFactory);
        
        netSalesCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setStyle("");
                } else {
                    setText(CurrencyUtil.format(item));
                    setStyle("-fx-font-weight: 800; -fx-text-fill: #0891b2; -fx-font-size: 13px;");
                }
            }
        });

        transactionsCol.setStyle("-fx-alignment: CENTER;");
        cashCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        upiCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        debitCardCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        creditCardCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        giftCardCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        salesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        refundsCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        netSalesCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        periodCol.setPrefWidth(150);
        transactionsCol.setPrefWidth(100);
        cashCol.setPrefWidth(100);
        upiCol.setPrefWidth(100);
        debitCardCol.setPrefWidth(100);
        creditCardCol.setPrefWidth(100);
        giftCardCol.setPrefWidth(100);
        salesCol.setPrefWidth(120);
        refundsCol.setPrefWidth(100);
        netSalesCol.setPrefWidth(120);
    }

    public void setupColumnFilter(javafx.scene.layout.VBox container, List<Label> totalLabels) {
        List<TableColumn<BreakdownItem, ?>> columns = List.of(
            periodCol, transactionsCol, cashCol, upiCol, debitCardCol, creditCardCol, 
            giftCardCol, salesCol, refundsCol, netSalesCol
        );
        
        MultiSelectFilter<TableColumn<BreakdownItem, ?>> columnFilter = new MultiSelectFilter<>(
            "All Columns",
            TableColumn::getText
        );
        columnFilter.setItems(columns);
        columnFilter.setPrefWidth(160);
        columnFilter.selectItems(columns);
        
        columnFilter.getSelectedItems().addListener((ListChangeListener<TableColumn<BreakdownItem, ?>>) c -> {
            Platform.runLater(() -> {
                List<TableColumn<BreakdownItem, ?>> selected = new java.util.ArrayList<>(columnFilter.getSelectedItems());
                for (TableColumn<BreakdownItem, ?> col : columns) {
                    col.setVisible(selected.contains(col));
                }
                breakdownTable.getTableView().refresh();
                updateTotals();
            });
        });
        
        container.getChildren().add(columnFilter);

        // Bind visibility for total labels
        for (int i = 0; i < columns.size(); i++) {
            totalLabels.get(i).visibleProperty().bind(columns.get(i).visibleProperty());
            totalLabels.get(i).managedProperty().bind(columns.get(i).visibleProperty());
            totalLabels.get(i).prefWidthProperty().bind(columns.get(i).widthProperty());
        }
    }

    public void updateTotals() {
        List<BreakdownItem> items = breakdownTable.getTableView().getItems();
        int totalTransactions = 0;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal upi = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal gift = BigDecimal.ZERO;
        BigDecimal totalSalesSum = BigDecimal.ZERO;
        BigDecimal totalRefundsSum = BigDecimal.ZERO;
        BigDecimal totalNetSalesSum = BigDecimal.ZERO;

        for (BreakdownItem item : items) {
            if (item == null) continue;
            if (transactionsCol.isVisible()) totalTransactions += item.totalTransactions();
            if (cashCol.isVisible()) cash = cash.add(item.cash() != null ? item.cash() : BigDecimal.ZERO);
            if (upiCol.isVisible()) upi = upi.add(item.upi() != null ? item.upi() : BigDecimal.ZERO);
            if (debitCardCol.isVisible()) debit = debit.add(item.debitCard() != null ? item.debitCard() : BigDecimal.ZERO);
            if (creditCardCol.isVisible()) credit = credit.add(item.creditCard() != null ? item.creditCard() : BigDecimal.ZERO);
            if (giftCardCol.isVisible()) gift = gift.add(item.giftCard() != null ? item.giftCard() : BigDecimal.ZERO);
            
            totalSalesSum = totalSalesSum.add(calculateDynamicGrossSales(item));
            if (refundsCol.isVisible()) totalRefundsSum = totalRefundsSum.add(item.refunds() != null ? item.refunds() : BigDecimal.ZERO);
            totalNetSalesSum = totalNetSalesSum.add(calculateDynamicNetSales(item));
        }

        totalLabels.get(1).setText(String.valueOf(totalTransactions));
        totalLabels.get(2).setText(CurrencyUtil.format(cash));
        totalLabels.get(3).setText(CurrencyUtil.format(upi));
        totalLabels.get(4).setText(CurrencyUtil.format(debit));
        totalLabels.get(5).setText(CurrencyUtil.format(credit));
        totalLabels.get(6).setText(CurrencyUtil.format(gift));
        totalLabels.get(7).setText(CurrencyUtil.format(totalSalesSum));
        totalLabels.get(8).setText(CurrencyUtil.format(totalRefundsSum));
        totalLabels.get(9).setText(CurrencyUtil.format(totalNetSalesSum));
    }

    public BigDecimal calculateDynamicGrossSales(BreakdownItem item) {
        if (item == null) return BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        if (cashCol.isVisible() && item.cash() != null) gross = gross.add(item.cash());
        if (upiCol.isVisible() && item.upi() != null) gross = gross.add(item.upi());
        if (debitCardCol.isVisible() && item.debitCard() != null) gross = gross.add(item.debitCard());
        if (creditCardCol.isVisible() && item.creditCard() != null) gross = gross.add(item.creditCard());
        if (giftCardCol.isVisible() && item.giftCard() != null) gross = gross.add(item.giftCard());
        return gross;
    }

    public BigDecimal calculateDynamicNetSales(BreakdownItem item) {
        if (item == null) return BigDecimal.ZERO;
        BigDecimal gross = calculateDynamicGrossSales(item);
        BigDecimal deductions = BigDecimal.ZERO;
        if (refundsCol.isVisible() && item.refunds() != null) deductions = deductions.add(item.refunds());
        return gross.subtract(deductions);
    }

    public boolean isColumnVisible(String name) {
        return switch (name) {
            case "Cash" -> cashCol.isVisible();
            case "UPI" -> upiCol.isVisible();
            case "Debit Card" -> debitCardCol.isVisible();
            case "Credit Card" -> creditCardCol.isVisible();
            case "Gift Card" -> giftCardCol.isVisible();
            case "Refunds" -> refundsCol.isVisible();
            default -> true;
        };
    }
}

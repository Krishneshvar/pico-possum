package com.picopossum.ui.reports;

import com.picopossum.application.reports.dto.BreakdownItem;
import com.picopossum.ui.common.controls.DataTableView;
import com.picopossum.ui.common.controls.MultiSelectFilter;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import com.picopossum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;

public class SalesReportTableManager {

    private final DataTableView<BreakdownItem> breakdownTable;
    
    private TableColumn<BreakdownItem, String> periodCol;
    private TableColumn<BreakdownItem, Integer> transactionsCol;
    private TableColumn<BreakdownItem, BigDecimal> cashCol;
    private TableColumn<BreakdownItem, BigDecimal> upiCol;
    private TableColumn<BreakdownItem, BigDecimal> cardCol;
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
        cardCol = new TableColumn<>("Card");
        giftCardCol = new TableColumn<>("Gift Card");
        salesCol = new TableColumn<>("Gross Sales");
        refundsCol = new TableColumn<>("Refunds");
        netSalesCol = new TableColumn<>("Net Sales");

        breakdownTable.getTableView().getColumns().clear();
        breakdownTable.getTableView().getColumns().addAll(List.of(
            periodCol, transactionsCol, cashCol, upiCol, cardCol, 
            giftCardCol, salesCol, refundsCol, netSalesCol
        ));
        
        breakdownTable.setEmptyMessage("No analytics records found");
        breakdownTable.setEmptySubtitle("Try adjusting your date range or filter criteria.");

        periodCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        transactionsCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(calculateDynamicTransactions(cellData.getValue())));
        cashCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().cash()));
        upiCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().upi()));
        cardCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().card()));
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
        cardCol.setCellFactory(currencyCellFactory);
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
        cardCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        giftCardCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        salesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        refundsCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        netSalesCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        periodCol.setPrefWidth(150);
        transactionsCol.setPrefWidth(100);
        cashCol.setPrefWidth(100);
        upiCol.setPrefWidth(100);
        cardCol.setPrefWidth(100);
        giftCardCol.setPrefWidth(100);
        salesCol.setPrefWidth(120);
        refundsCol.setPrefWidth(100);
        netSalesCol.setPrefWidth(120);
    }

    public void setupColumnFilter(javafx.scene.layout.VBox container, List<Label> totalLabels) {
        List<TableColumn<BreakdownItem, ?>> columns = List.of(
            periodCol, transactionsCol, cashCol, upiCol, cardCol, 
            giftCardCol, salesCol, refundsCol, netSalesCol
        );
        
        MultiSelectFilter<TableColumn<BreakdownItem, ?>> columnFilter = new MultiSelectFilter<>(
            "All Columns",
            col -> {
                if (col == periodCol) return "Date";
                if (col == transactionsCol) return "Transactions";
                if (col == cashCol) return "Cash";
                if (col == upiCol) return "UPI";
                if (col == cardCol) return "Card";
                if (col == giftCardCol) return "Gift Card";
                if (col == salesCol) return "Gross Sales";
                if (col == refundsCol) return "Refunds";
                if (col == netSalesCol) return "Net Sales";
                return col.getText();
            }
        );
        columnFilter.setItems(columns);
        columnFilter.setPrefWidth(160);
        columnFilter.selectItems(columns);
        
        // Initialize with all columns
        updateVisibleColumns(new java.util.ArrayList<>(columns));
        
        columnFilter.getSelectedItems().addListener((ListChangeListener<TableColumn<BreakdownItem, ?>>) c -> {
            Platform.runLater(() -> {
                List<TableColumn<BreakdownItem, ?>> selected = new java.util.ArrayList<>(columnFilter.getSelectedItems());
                updateVisibleColumns(selected);
                updateTotals();
            });
        });
        
        container.getChildren().add(columnFilter);
    }

    private void updateVisibleColumns(List<TableColumn<BreakdownItem, ?>> selected) {
        List<TableColumn<BreakdownItem, ?>> allCols = List.of(
            periodCol, transactionsCol, cashCol, upiCol, cardCol, 
            giftCardCol, salesCol, refundsCol, netSalesCol
        );

        for (TableColumn<BreakdownItem, ?> col : allCols) {
            col.setVisible(selected.contains(col));
        }

        // Rebuild table columns to avoid visibility glitches in constrained resize policies
        breakdownTable.getTableView().getColumns().setAll(selected);

        // Update total labels visibility and alignment
        for (int i = 0; i < allCols.size(); i++) {
            TableColumn<BreakdownItem, ?> col = allCols.get(i);
            Label label = totalLabels.get(i);
            boolean isVisible = col.isVisible();

            label.setVisible(isVisible);
            label.setManaged(isVisible);
            
            if (isVisible) {
                col.widthProperty().removeListener(this::handleColumnWidthChange);
                col.widthProperty().addListener(this::handleColumnWidthChange);
                label.setPrefWidth(col.getWidth());
            } else {
                col.widthProperty().removeListener(this::handleColumnWidthChange);
                label.setPrefWidth(0);
            }
        }
        
        // Refresh table to trigger cell factory updates
        breakdownTable.getTableView().refresh();
    }

    private void handleColumnWidthChange(javafx.beans.value.ObservableValue<? extends Number> obs, Number old, Number nw) {
        // Find which column changed and update its label
        List<TableColumn<BreakdownItem, ?>> allCols = List.of(
            periodCol, transactionsCol, cashCol, upiCol, cardCol, 
            giftCardCol, salesCol, refundsCol, netSalesCol
        );
        for (int i = 0; i < allCols.size(); i++) {
            if (allCols.get(i).widthProperty() == obs) {
                totalLabels.get(i).setPrefWidth(nw.doubleValue());
                break;
            }
        }
    }

    public void updateTotals() {
        List<BreakdownItem> items = breakdownTable.getTableView().getItems();
        int totalTransactions = 0;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal upi = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        BigDecimal gift = BigDecimal.ZERO;
        BigDecimal totalSalesSum = BigDecimal.ZERO;
        BigDecimal totalRefundsSum = BigDecimal.ZERO;
        BigDecimal totalNetSalesSum = BigDecimal.ZERO;

        for (BreakdownItem item : items) {
            if (item == null) continue;
            totalTransactions += calculateDynamicTransactions(item);
            if (cashCol.isVisible()) cash = cash.add(item.cash() != null ? item.cash() : BigDecimal.ZERO);
            if (upiCol.isVisible()) upi = upi.add(item.upi() != null ? item.upi() : BigDecimal.ZERO);
            if (cardCol.isVisible()) card = card.add(item.card() != null ? item.card() : BigDecimal.ZERO);
            if (giftCardCol.isVisible()) gift = gift.add(item.giftCard() != null ? item.giftCard() : BigDecimal.ZERO);
            
            totalSalesSum = totalSalesSum.add(calculateDynamicGrossSales(item));
            if (refundsCol.isVisible()) totalRefundsSum = totalRefundsSum.add(item.refunds() != null ? item.refunds() : BigDecimal.ZERO);
            totalNetSalesSum = totalNetSalesSum.add(calculateDynamicNetSales(item));
        }

        totalLabels.get(1).setText(String.valueOf(totalTransactions));
        totalLabels.get(2).setText(CurrencyUtil.format(cash));
        totalLabels.get(3).setText(CurrencyUtil.format(upi));
        totalLabels.get(4).setText(CurrencyUtil.format(card));
        totalLabels.get(5).setText(CurrencyUtil.format(gift));
        totalLabels.get(6).setText(CurrencyUtil.format(totalSalesSum));
        totalLabels.get(7).setText(CurrencyUtil.format(totalRefundsSum));
        totalLabels.get(8).setText(CurrencyUtil.format(totalNetSalesSum));
    }

    public BigDecimal calculateDynamicGrossSales(BreakdownItem item) {
        if (item == null) return BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        if (cashCol.isVisible() && item.cash() != null) gross = gross.add(item.cash());
        if (upiCol.isVisible() && item.upi() != null) gross = gross.add(item.upi());
        if (cardCol.isVisible() && item.card() != null) gross = gross.add(item.card());
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

    public int calculateDynamicTransactions(BreakdownItem item) {
        if (item == null) return 0;
        if (!transactionsCol.isVisible()) return 0;
        
        int count = 0;
        boolean anyPaymentColVisible = cashCol.isVisible() || upiCol.isVisible() || cardCol.isVisible() || giftCardCol.isVisible();
        
        if (!anyPaymentColVisible) return item.totalTransactions();
        
        if (cashCol.isVisible()) count += item.cashCount();
        if (upiCol.isVisible()) count += item.upiCount();
        if (cardCol.isVisible()) count += item.cardCount();
        if (giftCardCol.isVisible()) count += item.giftCardCount();
        
        return count;
    }

    public boolean isColumnVisible(String name) {
        return switch (name) {
            case "Date", "Period" -> periodCol.isVisible();
            case "Transactions" -> transactionsCol.isVisible();
            case "Cash" -> cashCol.isVisible();
            case "UPI" -> upiCol.isVisible();
            case "Card" -> cardCol.isVisible();
            case "Gift Card" -> giftCardCol.isVisible();
            case "Gross Sales" -> salesCol.isVisible();
            case "Refunds" -> refundsCol.isVisible();
            case "Net Sales" -> netSalesCol.isVisible();
            default -> true;
        };
    }
}

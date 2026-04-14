package com.possum.ui.sales;

import com.possum.domain.model.SaleItem;
import com.possum.ui.common.controls.DataTableView;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.kordamp.ikonli.javafx.FontIcon;
import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;

public class SaleDetailTableManager {

    private final DataTableView<SaleItem> itemsTable;
    private final DataTableView<SaleItem> returnedItemsTable;
    private boolean isEditingMode = false;
    private final Runnable onDataChanged;

    public SaleDetailTableManager(DataTableView<SaleItem> itemsTable, 
                                  DataTableView<SaleItem> returnedItemsTable,
                                  Runnable onDataChanged) {
        this.itemsTable = itemsTable;
        this.returnedItemsTable = returnedItemsTable;
        this.onDataChanged = onDataChanged;
    }

    public void setEditingMode(boolean editingMode) {
        this.isEditingMode = editingMode;
        setupActiveItemsTable();
    }

    public void setupActiveItemsTable() {
        TableColumn<SaleItem, String> productCol = new TableColumn<>("Product / Variant");
        productCol.setMinWidth(250);
        productCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().productName() + (data.getValue().variantName() != null ? " - " + data.getValue().variantName() : "")
        ));

        TableColumn<SaleItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setPrefWidth(100);
        qtyCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(
                data.getValue().quantity() - (data.getValue().returnedQuantity() != null ? data.getValue().returnedQuantity() : 0)
        ).asObject());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer qty, boolean empty) {
                super.updateItem(qty, empty);
                if (empty || qty == null) {
                    setGraphic(null);
                    setText(null);
                } else if (isEditingMode) {
                    javafx.scene.control.Spinner<Integer> spinner = new javafx.scene.control.Spinner<>(0, 1000, qty);
                    spinner.setEditable(true);
                    spinner.setPrefWidth(80);
                    spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                        int index = getIndex();
                        if (index >= 0 && index < getTableView().getItems().size()) {
                            SaleItem current = getTableView().getItems().get(index);
                            getTableView().getItems().set(index, new SaleItem(
                                    current.id(), current.saleId(), current.variantId(), current.variantName(),
                                    current.sku(), current.productName(), newVal, current.pricePerUnit(),
                                    current.costPerUnit(), current.taxRate(), current.taxAmount(),
                                    current.appliedTaxRate(), current.appliedTaxAmount(), 
                                    current.taxRuleSnapshot(), current.discountAmount(), null
                            ));
                            onDataChanged.run();
                        }
                    });
                    setGraphic(spinner);
                    setText(null);
                } else {
                    setText(String.valueOf(qty));
                    setGraphic(null);
                }
            }
        });

        TableColumn<SaleItem, BigDecimal> priceCol = new TableColumn<>("Unit Price");
        priceCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().pricePerUnit()));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                    setGraphic(null);
                } else if (isEditingMode) {
                    javafx.scene.control.TextField field = new javafx.scene.control.TextField(price.toPlainString());
                    field.setPrefWidth(90);
                    field.focusedProperty().addListener((obs, oldF, newF) -> {
                        if (!newF) { // On blur
                            try {
                                BigDecimal newVal = new BigDecimal(field.getText());
                                int index = getIndex();
                                if (index >= 0 && index < getTableView().getItems().size()) {
                                    SaleItem current = getTableView().getItems().get(index);
                                    getTableView().getItems().set(index, new SaleItem(
                                            current.id(), current.saleId(), current.variantId(), current.variantName(),
                                            current.sku(), current.productName(), current.quantity(), newVal,
                                            current.costPerUnit(), current.taxRate(), current.taxAmount(),
                                            current.appliedTaxRate(), current.appliedTaxAmount(), 
                                            current.taxRuleSnapshot(), current.discountAmount(), null
                                    ));
                                    onDataChanged.run();
                                }
                            } catch (Exception e) {
                                field.setText(price.toPlainString());
                            }
                        }
                    });
                    setGraphic(field);
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(price));
                    setGraphic(null);
                }
            }
        });

        TableColumn<SaleItem, BigDecimal> taxCol = new TableColumn<>("Tax");
        taxCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().taxAmount()));
        setupCurrencyCell(taxCol);

        TableColumn<SaleItem, BigDecimal> discountCol = new TableColumn<>("Discount");
        discountCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().discountAmount()));
        setupCurrencyCell(discountCol);

        TableColumn<SaleItem, BigDecimal> totalCol = new TableColumn<>("Line Total");
        totalCol.setCellValueFactory(data -> {
            SaleItem item = data.getValue();
            BigDecimal base = item.pricePerUnit().multiply(BigDecimal.valueOf(item.quantity()));
            BigDecimal total = base.add(item.taxAmount()).subtract(item.discountAmount());
            return new SimpleObjectProperty<>(total);
        });
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal total, boolean empty) {
                super.updateItem(total, empty);
                if (empty || total == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(total));
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });

        TableColumn<SaleItem, Void> actionsCol = new TableColumn<>("");
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Button deleteBtn = new javafx.scene.control.Button();
            {
                deleteBtn.getStyleClass().add("danger-button");
                FontIcon trashIcon = new FontIcon("bx-trash");
                trashIcon.setIconSize(14);
                trashIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                deleteBtn.setGraphic(trashIcon);
                deleteBtn.setOnAction(e -> {
                    int index = getIndex();
                    if (index >= 0 && index < getTableView().getItems().size()) {
                        getTableView().getItems().remove(index);
                        onDataChanged.run();
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || !isEditingMode) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });

        itemsTable.getTableView().getColumns().clear();
        itemsTable.getTableView().getColumns().addAll(productCol, qtyCol, priceCol, taxCol, discountCol, totalCol);
        if (isEditingMode) {
            itemsTable.getTableView().getColumns().add(actionsCol);
        }
        
        itemsTable.getTableView().setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        itemsTable.setEmptyMessage("No active items in this bill");
    }

    public void setupReturnedItemsTable() {
        TableColumn<SaleItem, String> retProductCol = new TableColumn<>("Product / Variant");
        TableColumn<SaleItem, String> retSkuCol = new TableColumn<>("SKU");
        TableColumn<SaleItem, Integer> retQtyCol = new TableColumn<>("Returned Qty");
        TableColumn<SaleItem, BigDecimal> retPriceCol = new TableColumn<>("Unit Price");
        TableColumn<SaleItem, BigDecimal> retRefundCol = new TableColumn<>("Refund Amount");

        returnedItemsTable.getTableView().getColumns().clear();
        returnedItemsTable.getTableView().getColumns().addAll(List.of(retProductCol, retSkuCol, retQtyCol, retPriceCol, retRefundCol));
        returnedItemsTable.getTableView().setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        returnedItemsTable.setEmptyMessage("No returned items recorded");

        retProductCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().productName() + (data.getValue().variantName() != null ? " - " + data.getValue().variantName() : "")
        ));
        
        retSkuCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sku()));
        
        retQtyCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().returnedQuantity()));
        
        retPriceCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().pricePerUnit()));
        setupCurrencyCell(retPriceCol);
        
        retRefundCol.setCellValueFactory(data -> {
            SaleItem item = data.getValue();
            BigDecimal price = item.pricePerUnit() != null ? item.pricePerUnit() : BigDecimal.ZERO;
            BigDecimal qty = BigDecimal.valueOf(item.returnedQuantity() != null ? item.returnedQuantity() : 0);
            return new SimpleObjectProperty<>(price.multiply(qty));
        });
        setupCurrencyCell(retRefundCol);
    }

    private void setupCurrencyCell(TableColumn<SaleItem, BigDecimal> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyUtil.format(item));
            }
        });
    }

    public void setItems(ObservableList<SaleItem> activeItems, ObservableList<SaleItem> returnedItems) {
        itemsTable.setItems(activeItems);
        returnedItemsTable.setItems(returnedItems);
    }
}

package com.picopossum.ui.sales;

import com.picopossum.domain.model.SaleItem;
import com.picopossum.ui.common.controls.DataTableView;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.kordamp.ikonli.javafx.FontIcon;
import com.picopossum.shared.util.CurrencyUtil;
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
        TableColumn<SaleItem, String> productCol = new TableColumn<>("Product");
        productCol.setMinWidth(250);
        productCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));

        TableColumn<SaleItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setPrefWidth(100);
        qtyCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(
                data.getValue().quantity() - (data.getValue().returnedQuantity() != null ? data.getValue().returnedQuantity() : 0)
        ).asObject());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Spinner<Integer> spinner = new javafx.scene.control.Spinner<>();
            private boolean updating = false;

            {
                spinner.setEditable(true);
                spinner.setPrefWidth(80);
                spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (updating || newVal == null) return;
                    
                    int index = getIndex();
                    if (index >= 0 && index < getTableView().getItems().size()) {
                        SaleItem current = getTableView().getItems().get(index);
                        int returnedQty = current.returnedQuantity() != null ? current.returnedQuantity() : 0;
                        int newGrossQty = newVal + returnedQty;
                        
                        // Safety check: SaleItem must have quantity > 0
                        if (newGrossQty > 0 && newGrossQty != current.quantity()) {
                            updating = true;
                            try {
                                getTableView().getItems().set(index, new SaleItem(
                                        current.id(), current.saleId(), current.productId(),
                                        current.sku(), current.productName(), newGrossQty, current.pricePerUnit(),
                                        current.costPerUnit(), current.discountAmount(), current.taxRate(), current.taxAmount(), current.returnedQuantity()
                                ));
                                onDataChanged.run();
                            } finally {
                                updating = false;
                            }
                        }
                    }
                });
            }

            @Override protected void updateItem(Integer netQty, boolean empty) {
                super.updateItem(netQty, empty);
                if (empty || netQty == null) {
                    setGraphic(null);
                    setText(null);
                } else if (isEditingMode) {
                    updating = true;
                    try {
                        SaleItem current = getTableView().getItems().get(getIndex());
                        int returnedQty = current.returnedQuantity() != null ? current.returnedQuantity() : 0;
                        // Min net quantity should ensure gross quantity is at least 1
                        int minNet = Math.max(0, 1 - returnedQty);
                        
                        javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory factory = 
                            (javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
                        
                        if (factory == null) {
                            spinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(minNet, 10000, netQty));
                        } else {
                            factory.setMin(minNet);
                            factory.setValue(netQty);
                        }
                    } finally {
                        updating = false;
                    }
                    setGraphic(spinner);
                    setText(null);
                } else {
                    setText(String.valueOf(netQty));
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
                                            current.id(), current.saleId(), current.productId(),
                                            current.sku(), current.productName(), current.quantity(), newVal,
                                            current.costPerUnit(), current.discountAmount(), current.taxRate(), current.taxAmount(), current.returnedQuantity()
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



        TableColumn<SaleItem, BigDecimal> discountCol = new TableColumn<>("Discount");
        discountCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().discountAmount()));
        setupCurrencyCell(discountCol);

        TableColumn<SaleItem, BigDecimal> totalCol = new TableColumn<>("Line Total");
        totalCol.setCellValueFactory(data -> {
            SaleItem item = data.getValue();
            BigDecimal base = item.pricePerUnit().multiply(BigDecimal.valueOf(item.quantity()));
            BigDecimal total = base.subtract(item.discountAmount());
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
        actionsCol.setMinWidth(50);
        actionsCol.setMaxWidth(50);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Button deleteBtn = new javafx.scene.control.Button();
            {
                deleteBtn.getStyleClass().add("btn-delete");
                FontIcon trashIcon = new FontIcon("bx-trash");
                trashIcon.setIconSize(16);
                deleteBtn.setGraphic(trashIcon);
                deleteBtn.setTooltip(new javafx.scene.control.Tooltip("Remove this item"));
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
        itemsTable.getTableView().getColumns().addAll(productCol, qtyCol, priceCol, discountCol, totalCol);
        if (isEditingMode) {
            itemsTable.getTableView().getColumns().add(actionsCol);
        }
        
        itemsTable.getTableView().setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        itemsTable.setEmptyMessage("No active items in this bill");
    }

    public void setupReturnedItemsTable() {
        TableColumn<SaleItem, String> retProductCol = new TableColumn<>("Product");
        TableColumn<SaleItem, String> retSkuCol = new TableColumn<>("SKU");
        TableColumn<SaleItem, Integer> retQtyCol = new TableColumn<>("Returned Qty");
        TableColumn<SaleItem, BigDecimal> retPriceCol = new TableColumn<>("Unit Price");
        TableColumn<SaleItem, BigDecimal> retRefundCol = new TableColumn<>("Refund Amount");

        returnedItemsTable.getTableView().getColumns().clear();
        returnedItemsTable.getTableView().getColumns().addAll(List.of(retProductCol, retSkuCol, retQtyCol, retPriceCol, retRefundCol));
        returnedItemsTable.getTableView().setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        returnedItemsTable.setEmptyMessage("No returned items recorded");

        retProductCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productName()));
        
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

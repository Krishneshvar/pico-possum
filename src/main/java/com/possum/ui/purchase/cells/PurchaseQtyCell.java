package com.possum.ui.purchase.cells;

import com.possum.ui.purchase.PurchaseItemRow;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;

public class PurchaseQtyCell extends TableCell<PurchaseItemRow, Integer> {
    private final Spinner<Integer> spinner = new Spinner<>(1, 10000, 1);
    private final PurchaseCellHandler handler;

    public PurchaseQtyCell(PurchaseCellHandler handler) {
        this.handler = handler;
        spinner.setEditable(true);
        spinner.setPrefWidth(70);
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            PurchaseItemRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null && newVal != null) {
                row.setQuantity(newVal);
                handler.recalculateTotal();
            }
        });
    }

    @Override
    protected void updateItem(Integer item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
            setGraphic(null);
        } else {
            PurchaseItemRow row = getTableRow().getItem();
            spinner.getValueFactory().setValue(row.getQuantity());
            spinner.setDisable(handler.isViewMode());
            setGraphic(spinner);
        }
    }
}

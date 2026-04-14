package com.possum.ui.purchase.cells;

import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.purchase.PurchaseItemRow;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public class PurchaseCostCell extends TableCell<PurchaseItemRow, BigDecimal> {
    private final TextField textField = new TextField();
    private final PurchaseCellHandler handler;

    public PurchaseCostCell(PurchaseCellHandler handler) {
        this.handler = handler;
        textField.setPrefWidth(90);
        textField.setAlignment(Pos.CENTER_RIGHT);
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            PurchaseItemRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null && newVal != null && !newVal.isEmpty()) {
                try {
                    BigDecimal val = new BigDecimal(newVal.replaceAll("[^\\d.]", ""));
                    if (val.compareTo(BigDecimal.ZERO) < 0) {
                        NotificationService.warning("Unit cost must be non-negative.");
                        textField.setText(oldVal != null ? oldVal : "0");
                        return;
                    }
                    if (!val.equals(row.getUnitCost())) {
                        row.setUnitCost(val);
                        handler.recalculateTotal();
                    }
                } catch (NumberFormatException e) {
                    NotificationService.warning("Unit cost must be a valid number.");
                    textField.setText(oldVal != null ? oldVal : "0");
                }
            }
        });
    }

    @Override
    protected void updateItem(BigDecimal item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
            setGraphic(null);
        } else {
            PurchaseItemRow row = getTableRow().getItem();
            String currentText = row.getUnitCost().toString();
            if (!textField.getText().equals(currentText)) {
                textField.setText(currentText);
            }
            textField.setDisable(handler.isViewMode());
            setGraphic(textField);
        }
    }
}

package com.possum.ui.purchase.cells;

import com.possum.ui.purchase.PurchaseItemRow;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public class PurchaseActionCell extends TableCell<PurchaseItemRow, Void> {
    private final Button deleteBtn;
    private final PurchaseCellHandler handler;

    public PurchaseActionCell(PurchaseCellHandler handler) {
        this.handler = handler;
        this.deleteBtn = new Button();
        FontIcon trashIcon = new FontIcon("bx-trash");
        trashIcon.setIconSize(16);
        deleteBtn.setGraphic(trashIcon);
        deleteBtn.getStyleClass().addAll("action-button");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ef4444; -fx-cursor: hand;");
        deleteBtn.setTooltip(new Tooltip("Remove from order"));
        deleteBtn.setOnAction(e -> {
            PurchaseItemRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                handler.removeRow(row);
                handler.recalculateTotal();
            }
        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || handler.isViewMode()) {
            setGraphic(null);
        } else {
            setGraphic(deleteBtn);
        }
    }
}

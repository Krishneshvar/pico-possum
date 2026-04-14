package com.possum.ui.sales.cells;

import com.possum.domain.model.CartItem;
import com.possum.ui.common.controls.NotificationService;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public class EditableQuantityCell extends TableCell<CartItem, CartItem> {
    private TextField tf;
    private final CartCellHandler handler;
    private final TableColumn<CartItem, ?> colQty;

    public EditableQuantityCell(CartCellHandler handler, TableColumn<CartItem, ?> colQty) {
        this.handler = handler;
        this.colQty = colQty;
    }

    @Override public void startEdit() { 
        super.startEdit(); 
        if (tf == null) tf = createTF(true); 
        setText(null); setGraphic(tf); tf.selectAll(); tf.requestFocus(); 
    }
    
    @Override public void cancelEdit() { 
        super.cancelEdit(); 
        if (getItem() != null) setText(String.valueOf(getItem().getQuantity())); 
        setGraphic(null); 
    }
    
    @Override public void updateItem(CartItem it, boolean e) { 
        super.updateItem(it, e); 
        if (e || it == null) { setText(null); setGraphic(null); } 
        else if (isEditing()) { if (tf != null) tf.setText(String.valueOf(it.getQuantity())); setText(null); setGraphic(tf); } 
        else { setText(String.valueOf(it.getQuantity())); setGraphic(null); } 
    }

    private TextField createTF(boolean center) { 
        TextField f = new TextField(); 
        f.getStyleClass().add("table-input"); 
        if (center) f.setAlignment(Pos.CENTER); 
        f.setPrefWidth(60); 
        f.setOnAction(ev -> commitEdit(getItem())); 
        f.focusedProperty().addListener((o, ol, fw) -> { if (!fw && isEditing()) commitEdit(getItem()); });
        f.setOnKeyPressed(ev -> { 
            if (ev.getCode() == KeyCode.ESCAPE) cancelEdit(); 
            else if (ev.getCode() == KeyCode.ENTER) { commitEdit(getItem()); handler.moveFocusNext(getIndex(), colQty); ev.consume(); } 
            else if (ev.getCode() == KeyCode.TAB) { commitEdit(getItem()); if (ev.isShiftDown()) handler.moveToPrevious(); else handler.moveToNext(); ev.consume(); } 
        });
        return f;
    }
    
    @Override public void commitEdit(CartItem it) {
        if (tf != null && it != null) {
            try {
                int n = Integer.parseInt(tf.getText().trim());
                int newQty = Math.max(1, n);
                if (handler.isInventoryRestrictionsEnabled() && it.getVariant().stock() != null && newQty > it.getVariant().stock()) {
                    NotificationService.warning("Insufficient stock! Available: " + it.getVariant().stock());
                    tf.setText(String.valueOf(it.getQuantity()));
                } else {
                    it.setQuantity(newQty);
                    handler.refreshCurrentBill();
                }

            } catch (Exception e) {
                cancelEdit();
                NotificationService.warning("Quantity must be a positive integer");
                return;
            }
        }
        super.commitEdit(it);
    }
}

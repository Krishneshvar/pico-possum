package com.possum.ui.sales.cells;

import com.possum.domain.model.CartItem;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;
import com.possum.shared.util.CurrencyUtil;

public class EditableDiscountAmtCell extends TableCell<CartItem, CartItem> {
    private TextField tf;
    private final CartCellHandler handler;
    private final TableColumn<CartItem, ?> colDiscountAmt;

    public EditableDiscountAmtCell(CartCellHandler handler, TableColumn<CartItem, ?> colDiscountAmt) {
        this.handler = handler;
        this.colDiscountAmt = colDiscountAmt;
    }

    @Override public void startEdit() { 
        super.startEdit(); 
        if (tf == null) tf = createTF(); 
        setText(null); setGraphic(tf); 
        BigDecimal a = getItem().getDiscountAmount(); 
        tf.setText(a.compareTo(BigDecimal.ZERO) == 0 ? "" : a.toString()); 
        tf.selectAll(); 
        tf.requestFocus(); 
    }

    @Override public void cancelEdit() { super.cancelEdit(); updateDisplay(); }
    @Override public void updateItem(CartItem it, boolean e) { super.updateItem(it, e); if (e || it == null) { setText(null); setGraphic(null); } else if (isEditing()) { setGraphic(tf); setText(null); } else updateDisplay(); }
    private void updateDisplay() { CartItem it = getItem(); if (it != null) setText(it.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0 ? "0" : CurrencyUtil.format(it.getDiscountAmount())); setGraphic(null); }

    private TextField createTF() { 
        TextField f = new TextField(); 
        f.getStyleClass().add("table-input"); 
        f.setAlignment(Pos.CENTER_RIGHT); 
        f.setOnAction(ev -> commitEdit(getItem())); 
        f.focusedProperty().addListener((o, ol, fw) -> { if (!fw && isEditing()) commitEdit(getItem()); });
        f.setOnKeyPressed(ev -> { 
            if (ev.getCode() == KeyCode.ENTER) { commitEdit(getItem()); handler.moveFocusNext(getIndex(), colDiscountAmt); ev.consume(); } 
            else if (ev.getCode() == KeyCode.TAB) { commitEdit(getItem()); if (ev.isShiftDown()) handler.moveToPrevious(); else handler.moveToNext(); ev.consume(); } 
            else if (ev.getCode() == KeyCode.ESCAPE) cancelEdit(); 
        });
        return f;
    }
    
    @Override public void commitEdit(CartItem it) { 
        if (tf != null && it != null) { 
            try { 
                String v = tf.getText().trim(); 
                it.setDiscountValue(v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v)); 
                it.setDiscountType("fixed"); 
                handler.refreshCurrentBill(); 
            } catch (Exception e) { 
                cancelEdit(); 
                return; 
            } 
        } 
        super.commitEdit(it); 
    }
}

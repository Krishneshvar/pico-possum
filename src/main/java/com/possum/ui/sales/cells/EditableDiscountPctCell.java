package com.possum.ui.sales.cells;

import com.possum.domain.model.CartItem;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EditableDiscountPctCell extends TableCell<CartItem, CartItem> {
    private TextField tf;
    private final CartCellHandler handler;
    private final TableColumn<CartItem, ?> colDiscountPct;

    public EditableDiscountPctCell(CartCellHandler handler, TableColumn<CartItem, ?> colDiscountPct) {
        this.handler = handler;
        this.colDiscountPct = colDiscountPct;
    }

    @Override public void startEdit() { 
        super.startEdit(); 
        if (tf == null) tf = createTF(); 
        setText(null); setGraphic(tf); 
        CartItem it = getItem(); 
        BigDecimal lT = it.getPricePerUnit().multiply(BigDecimal.valueOf(it.getQuantity())); 
        BigDecimal pct = it.getDiscountType().equals("pct") ? it.getDiscountValue() : (lT.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : it.getDiscountAmount().multiply(BigDecimal.valueOf(100)).divide(lT, 2, RoundingMode.HALF_UP)); 
        tf.setText(pct.compareTo(BigDecimal.ZERO) == 0 ? "" : pct.toString()); 
        tf.selectAll(); 
        tf.requestFocus(); 
    }

    @Override public void cancelEdit() { super.cancelEdit(); updateDisplay(); }
    @Override public void updateItem(CartItem it, boolean e) { super.updateItem(it, e); if (e || it == null) { setText(null); setGraphic(null); } else if (isEditing()) { setGraphic(tf); setText(null); } else updateDisplay(); }
    private void updateDisplay() { CartItem it = getItem(); if (it != null) { BigDecimal lT = it.getPricePerUnit().multiply(BigDecimal.valueOf(it.getQuantity())); BigDecimal pct = it.getDiscountType().equals("pct") ? it.getDiscountValue() : (lT.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : it.getDiscountAmount().multiply(BigDecimal.valueOf(100)).divide(lT, 2, RoundingMode.HALF_UP)); setText(pct.compareTo(BigDecimal.ZERO) == 0 ? "0%" : pct + "%"); } setGraphic(null); }

    private TextField createTF() { 
        TextField f = new TextField(); 
        f.getStyleClass().add("table-input"); 
        f.setAlignment(Pos.CENTER_RIGHT); 
        f.setOnAction(ev -> commitEdit(getItem())); 
        f.focusedProperty().addListener((o, ol, fw) -> { if (!fw && isEditing()) commitEdit(getItem()); });
        f.setOnKeyPressed(ev -> { 
            if (ev.getCode() == KeyCode.ENTER) { commitEdit(getItem()); handler.moveFocusNext(getIndex(), colDiscountPct); ev.consume(); } 
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
                it.setDiscountType("pct"); 
                handler.refreshCurrentBill(); 
            } catch (Exception e) { 
                cancelEdit(); 
                return; 
            } 
        } 
        super.commitEdit(it); 
    }
}

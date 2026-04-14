package com.possum.ui.sales.cells;

import com.possum.domain.model.CartItem;
import com.possum.ui.common.controls.NotificationService;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;

public class EditablePriceCell extends TableCell<CartItem, CartItem> {
    private TextField tf;
    private final CartCellHandler handler;
    private final TableColumn<CartItem, ?> colPrice;

    public EditablePriceCell(CartCellHandler handler, TableColumn<CartItem, ?> colPrice) {
        this.handler = handler;
        this.colPrice = colPrice;
    }

    @Override public void startEdit() { 
        super.startEdit(); 
        if (tf == null) tf = createTF(); 
        setText(null); setGraphic(tf); tf.selectAll(); tf.requestFocus(); 
    }
    
    @Override public void cancelEdit() { 
        super.cancelEdit(); 
        if (getItem() != null) setText(com.possum.shared.util.CurrencyUtil.format(getItem().getPricePerUnit())); 
        setGraphic(null); 
    }
    
    @Override public void updateItem(CartItem it, boolean e) { 
        super.updateItem(it, e); 
        if (e || it == null) { setText(null); setGraphic(null); } 
        else if (isEditing()) { if (tf != null) tf.setText(it.getPricePerUnit().toString()); setText(null); setGraphic(tf); } 
        else { setText(com.possum.shared.util.CurrencyUtil.format(it.getPricePerUnit())); setGraphic(null); } 
    }

    private TextField createTF() { 
        TextField f = new TextField(); 
        f.getStyleClass().add("table-input"); 
        f.setAlignment(Pos.CENTER_RIGHT); 
        f.setOnAction(ev -> commitEdit(getItem())); 
        f.focusedProperty().addListener((o, ol, fw) -> { if (!fw && isEditing()) commitEdit(getItem()); });
        f.setOnKeyPressed(ev -> { 
            if (ev.getCode() == KeyCode.ESCAPE) cancelEdit(); 
            else if (ev.getCode() == KeyCode.ENTER) { commitEdit(getItem()); handler.moveFocusNext(getIndex(), colPrice); ev.consume(); } 
            else if (ev.getCode() == KeyCode.TAB) { commitEdit(getItem()); if (ev.isShiftDown()) handler.moveToPrevious(); else handler.moveToNext(); ev.consume(); } 
        });
        return f;
    }
    
    public void commitEdit(CartItem it) { 
        if (tf != null && it != null) { 
            try { 
                BigDecimal v = new BigDecimal(tf.getText().replace(com.possum.shared.util.CurrencyUtil.getSymbol(), "").replace(",", "").trim()).max(BigDecimal.ZERO); 
                BigDecimal m = it.getVariant().price(); 
                if (v.compareTo(m) > 0) { 
                    NotificationService.warning("Price cannot exceed MRP (" + com.possum.shared.util.CurrencyUtil.format(m) + ")"); 
                    v = m; 
                } 
                it.setPricePerUnit(v); 
                handler.refreshCurrentBill(); 
            } catch (Exception e) { 
                cancelEdit(); 
                return; 
            } 
        } 
        super.commitEdit(it); 
    }
}

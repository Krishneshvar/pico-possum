package com.possum.ui.sales.cells;

import com.possum.domain.model.CartItem;
import javafx.scene.control.TableColumn;

public interface CartCellHandler {
    void refreshCurrentBill();
    boolean isInventoryRestrictionsEnabled();
    void moveFocusNext(int row, TableColumn<CartItem, ?> currentColumn);
    void moveToNext();
    void moveToPrevious();
}

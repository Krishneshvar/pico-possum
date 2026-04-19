package com.picopossum.ui.sales.cells;

import com.picopossum.domain.model.CartItem;
import javafx.scene.control.TableColumn;

public interface CartCellHandler {
    void refreshCurrentBill();
    boolean isInventoryRestrictionsEnabled();
    void moveFocusNext(int row, TableColumn<CartItem, ?> currentColumn);
    void moveToNext();
    void moveToPrevious();
}

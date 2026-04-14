package com.possum.ui.purchase.cells;

import com.possum.ui.purchase.PurchaseItemRow;

public interface PurchaseCellHandler {
    void recalculateTotal();
    void removeRow(PurchaseItemRow row);
    boolean isViewMode();
}

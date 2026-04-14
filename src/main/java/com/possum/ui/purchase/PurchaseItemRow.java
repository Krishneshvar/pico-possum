package com.possum.ui.purchase;

import com.possum.domain.model.Product;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;

public class PurchaseItemRow {
    private final Product product;
    private final IntegerProperty quantity = new SimpleIntegerProperty();
    private final ObjectProperty<BigDecimal> unitCost = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> total = new SimpleObjectProperty<>();

    public PurchaseItemRow(Product product) {
        this.product = product;
        this.quantity.set(1);
        this.unitCost.set(product.costPrice() != null ? product.costPrice() : BigDecimal.ZERO);
        total.bind(Bindings.createObjectBinding(() -> getUnitCost().multiply(BigDecimal.valueOf(getQuantity())), quantity, unitCost));
    }

    public Long getProductId() { return product != null ? product.id() : null; }
    public String getProductName() { return product != null ? product.name() : ""; }
    public String getSku() { return product != null ? product.sku() : ""; }
    public String getDisplayName() { return product != null ? product.name() + " (" + product.sku() + ")" : ""; }
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int q) { this.quantity.set(q); }
    public IntegerProperty quantityProperty() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost.get(); }
    public void setUnitCost(BigDecimal c) { this.unitCost.set(c); }
    public ObjectProperty<BigDecimal> unitCostProperty() { return unitCost; }
    public BigDecimal getTotal() { return total.get(); }
    public ObjectProperty<BigDecimal> totalProperty() { return total; }
}

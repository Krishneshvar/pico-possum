package com.possum.ui.purchase;

import com.possum.domain.model.Variant;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;

public class PurchaseItemRow {
    private final Variant variant;
    private final IntegerProperty quantity = new SimpleIntegerProperty();
    private final ObjectProperty<BigDecimal> unitCost = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> total = new SimpleObjectProperty<>();

    public PurchaseItemRow(Variant variant) {
        this.variant = variant;
        this.quantity.set(1);
        this.unitCost.set(variant.costPrice() != null ? variant.costPrice() : BigDecimal.ZERO);
        total.bind(Bindings.createObjectBinding(() -> getUnitCost().multiply(BigDecimal.valueOf(getQuantity())), quantity, unitCost));
    }

    public Long getVariantId() { return variant != null ? variant.id() : null; }
    public String getProductName() { return variant != null ? variant.productName() : ""; }
    public String getVariantName() { return variant != null ? variant.name() : ""; }
    public String getSku() { return variant != null ? variant.sku() : ""; }
    public String getDisplayName() { return variant != null ? variant.productName() + " - " + variant.name() : ""; }
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int q) { this.quantity.set(q); }
    public IntegerProperty quantityProperty() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost.get(); }
    public void setUnitCost(BigDecimal c) { this.unitCost.set(c); }
    public ObjectProperty<BigDecimal> unitCostProperty() { return unitCost; }
    public BigDecimal getTotal() { return total.get(); }
    public ObjectProperty<BigDecimal> totalProperty() { return total; }
}

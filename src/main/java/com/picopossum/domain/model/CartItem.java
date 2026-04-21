package com.picopossum.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CartItem {
    private Product product;
    private int quantity;
    private BigDecimal pricePerUnit;
    private String discountType = "fixed"; // "fixed" or "pct"
    private BigDecimal discountValue = BigDecimal.ZERO;
    
    // Calculated fields
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal netLineTotal = BigDecimal.ZERO;

    public CartItem() {}

    public CartItem(Product product, int quantity) {
        if (product == null) throw new IllegalArgumentException("Product cannot be null");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        this.product = product;
        this.quantity = quantity;
        this.pricePerUnit = product.mrp();
        calculateBasics();
    }

    public void calculateBasics() {
        BigDecimal lineGross = pricePerUnit.multiply(BigDecimal.valueOf(quantity));
        if ("pct".equals(discountType)) {
            this.discountAmount = lineGross.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            this.discountAmount = discountValue;
        }
        this.netLineTotal = lineGross.subtract(discountAmount).max(BigDecimal.ZERO);
    }

    // Getters and Setters
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { 
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        this.quantity = quantity; 
        calculateBasics();
    }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { 
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price per unit cannot be null or negative");
        }
        this.pricePerUnit = pricePerUnit; 
        calculateBasics();
    }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { 
        if (!"fixed".equals(discountType) && !"pct".equals(discountType)) {
            throw new IllegalArgumentException("Discount type must be 'fixed' or 'pct'");
        }
        this.discountType = discountType; 
        calculateBasics();
    }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { 
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount value cannot be null or negative");
        }
        this.discountValue = discountValue; 
        calculateBasics();
    }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getNetLineTotal() { return netLineTotal; }
    public void setNetLineTotal(BigDecimal netLineTotal) { this.netLineTotal = netLineTotal; }

    public BigDecimal getGrossLineTotal() {
        return pricePerUnit.multiply(BigDecimal.valueOf(quantity));
    }
}

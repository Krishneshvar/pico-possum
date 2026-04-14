package com.possum.domain.model;

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
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private String taxRuleSnapshot;

    public CartItem() {}

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.pricePerUnit = product.mrp();
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
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getNetLineTotal() { return netLineTotal; }
    public void setNetLineTotal(BigDecimal netLineTotal) { this.netLineTotal = netLineTotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public String getTaxRuleSnapshot() { return taxRuleSnapshot; }
    public void setTaxRuleSnapshot(String taxRuleSnapshot) { this.taxRuleSnapshot = taxRuleSnapshot; }

    public BigDecimal getGrossLineTotal() {
        return pricePerUnit.multiply(BigDecimal.valueOf(quantity));
    }
}

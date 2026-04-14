package com.possum.application.sales.dto;

import java.math.BigDecimal;

public class TaxableItem {
    private final String productName;
    private final BigDecimal price;
    private final int quantity;
    private final Long taxCategoryId;
    private final long productId;
    
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private String taxRuleSnapshot;
    
    public TaxableItem(String productName, BigDecimal price, int quantity,
                       Long taxCategoryId, long productId) {
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.taxCategoryId = taxCategoryId;
        this.productId = productId;
    }
    
    public BigDecimal getLineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
    
    public String getProductName() { return productName; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public Long getTaxCategoryId() { return taxCategoryId; }
    public long getProductId() { return productId; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public String getTaxRuleSnapshot() { return taxRuleSnapshot; }
    
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public void setTaxRuleSnapshot(String taxRuleSnapshot) { this.taxRuleSnapshot = taxRuleSnapshot; }
}

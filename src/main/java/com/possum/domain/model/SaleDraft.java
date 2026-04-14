package com.possum.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SaleDraft {
    private int index;
    private List<CartItem> items = new ArrayList<>();

    public void addItem(CartItem item) {
        this.items.add(item);
    }

    private Customer selectedCustomer;
    private String customerName = "";
    private String customerPhone = "";
    private String customerEmail = "";
    private String customerAddress = "";
    private PaymentMethod selectedPaymentMethod;
    private boolean fullPayment = true;
    private BigDecimal overallDiscountValue = BigDecimal.ZERO;
    private boolean isDiscountFixed = true;
    private BigDecimal amountTendered = BigDecimal.ZERO;
    
    // Summary fields
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discountTotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private BigDecimal totalMrp = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public void reset() {
        items.clear();
        selectedCustomer = null;
        customerName = "";
        customerPhone = "";
        customerEmail = "";
        customerAddress = "";
        selectedPaymentMethod = null;
        fullPayment = true;
        overallDiscountValue = BigDecimal.ZERO;
        isDiscountFixed = true;
        amountTendered = BigDecimal.ZERO;
        subtotal = BigDecimal.ZERO;
        discountTotal = BigDecimal.ZERO;
        taxAmount = BigDecimal.ZERO;
        total = BigDecimal.ZERO;
        totalMrp = BigDecimal.ZERO;
        totalPrice = BigDecimal.ZERO;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public Customer getSelectedCustomer() { return selectedCustomer; }
    public void setSelectedCustomer(Customer customer) { this.selectedCustomer = customer; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public PaymentMethod getSelectedPaymentMethod() { return selectedPaymentMethod; }
    public void setSelectedPaymentMethod(PaymentMethod method) { this.selectedPaymentMethod = method; }
    public boolean isFullPayment() { return fullPayment; }
    public void setFullPayment(boolean fullPayment) { this.fullPayment = fullPayment; }
    public BigDecimal getOverallDiscountValue() { return overallDiscountValue; }
    public void setOverallDiscountValue(BigDecimal val) { this.overallDiscountValue = val; }
    public boolean isDiscountFixed() { return isDiscountFixed; }
    public void setDiscountFixed(boolean discountFixed) { isDiscountFixed = discountFixed; }
    public BigDecimal getAmountTendered() { return amountTendered; }
    public void setAmountTendered(BigDecimal amountTendered) { this.amountTendered = amountTendered; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getTotalMrp() { return totalMrp; }
    public void setTotalMrp(BigDecimal totalMrp) { this.totalMrp = totalMrp; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}

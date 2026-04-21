package com.picopossum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Sale(
        Long id,
        String invoiceNumber,
        LocalDateTime saleDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal discount,
        String status,
        String fulfillmentStatus,
        Long customerId,
        String customerName,
        String customerPhone,
        String customerEmail,
        String billerName,
        Long paymentMethodId,
        String paymentMethodName,
        String invoiceId
) {
    public Sale {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Sale total amount cannot be null or negative");
        }
        if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Sale paid amount cannot be null or negative");
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Sale discount cannot be null or negative");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Sale status must be explicitly provided");
        }
    }
    public String shortInvoiceNumber() {
        String base = invoiceId != null ? invoiceId : invoiceNumber;
        if (base == null) return "";
        // Extract trailing digits (the sequence part)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)$").matcher(base);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return base;
    }
}

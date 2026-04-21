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

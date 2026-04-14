package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Sale(
        Long id,
        String invoiceNumber,
        LocalDateTime saleDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal discount,
        BigDecimal totalTax,
        String status,
        String fulfillmentStatus,
        Long customerId,
        Long userId,
        String customerName,
        String customerPhone,
        String customerEmail,
        String billerName,
        Long paymentMethodId,
        String paymentMethodName
) {
    public String shortInvoiceNumber() {
        if (invoiceNumber == null) return "";
        // Extract trailing digits (the sequence part)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)$").matcher(invoiceNumber);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return invoiceNumber;
    }
}

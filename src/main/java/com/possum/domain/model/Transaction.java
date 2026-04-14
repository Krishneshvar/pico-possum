package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Transaction(
        Long id,
        BigDecimal amount,
        String type,
        Long paymentMethodId,
        String paymentMethodName,
        String status,
        LocalDateTime transactionDate,
        String invoiceNumber,
        String customerName,
        String supplierName
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

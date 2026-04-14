package com.possum.domain.model;

import java.time.LocalDateTime;

public record PurchaseOrder(
        Long id,
        String invoiceNumber,
        Long supplierId,
        String supplierName,
        Long paymentMethodId,
        String paymentMethodName,
        String status,
        LocalDateTime orderDate,
        LocalDateTime receivedDate,
        Long createdBy,
        String createdByName,
        Integer itemCount,
        java.math.BigDecimal totalCost
) {
    public String shortInvoiceNumber() {
        if (invoiceNumber == null) return "PO-" + id;
        // Extract trailing digits (the sequence part)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)$").matcher(invoiceNumber);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return invoiceNumber;
    }
}

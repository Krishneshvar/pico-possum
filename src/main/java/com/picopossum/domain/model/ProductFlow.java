package com.picopossum.domain.model;

import java.time.LocalDateTime;

public record ProductFlow(
        Long id,
        Long productId,
        String eventType,
        Integer quantity,
        String referenceType,
        Long referenceId,
        String productName,
        String customerName,
        Long billRefId,
        String billRefNumber,
        String paymentMethodNames,
        LocalDateTime eventDate
) {
    public ProductFlow {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required for product flow");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type is required for product flow");
        }
        if (quantity == null || quantity == 0) {
            throw new IllegalArgumentException("Flow quantity must be non-zero");
        }
    }

    public String shortBillRefNumber() {
        if (billRefNumber == null) return "";
        // Extract trailing digits (the sequence part)
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)$").matcher(billRefNumber);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return billRefNumber;
    }
}

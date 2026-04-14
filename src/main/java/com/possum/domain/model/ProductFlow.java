package com.possum.domain.model;

import java.time.LocalDateTime;

public record ProductFlow(
        Long id,
        Long variantId,
        String eventType,
        Integer quantity,
        String referenceType,
        Long referenceId,
        String variantName,
        String productName,
        String customerName,
        Long billRefId,
        String billRefNumber,
        String paymentMethodNames,
        LocalDateTime eventDate
) {
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

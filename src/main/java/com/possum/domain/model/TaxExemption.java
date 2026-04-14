package com.possum.domain.model;

import java.time.LocalDateTime;

public record TaxExemption(
        Long id,
        Long customerId,
        String exemptionType,
        String certificateNumber,
        String reason,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        Long approvedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

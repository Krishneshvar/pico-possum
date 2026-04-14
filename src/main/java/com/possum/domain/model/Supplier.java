package com.possum.domain.model;

import java.time.LocalDateTime;

public record Supplier(
        Long id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String gstin,
        Long paymentPolicyId,
        String paymentPolicyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

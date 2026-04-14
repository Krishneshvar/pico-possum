package com.possum.domain.model;

import java.time.LocalDateTime;

public record Customer(
        Long id,
        String name,
        String phone,
        String email,
        String address,
        String customerType,
        Boolean isTaxExempt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

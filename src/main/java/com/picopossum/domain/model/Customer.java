package com.picopossum.domain.model;

import java.time.LocalDateTime;

public record Customer(
        Long id,
        String name,
        String phone,
        String email,
        String address,
        String customerType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
    public Customer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be null or blank");
        }
    }
}

package com.possum.domain.model;

import java.time.LocalDateTime;

public record PaymentPolicy(
        Long id,
        String name,
        Integer daysToPay,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

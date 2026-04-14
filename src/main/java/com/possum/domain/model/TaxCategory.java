package com.possum.domain.model;

import java.time.LocalDateTime;

public record TaxCategory(
        Long id,
        String name,
        String description,
        Integer productCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.picopossum.domain.model;

import java.time.LocalDateTime;

public record Category(
        Long id,
        String name,
        Long parentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
    public Category {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
    }
}

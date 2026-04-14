package com.possum.domain.model;

import java.time.LocalDateTime;

public record Product(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long taxCategoryId,
        String taxCategoryName,
        String status,
        String imagePath,
        Integer stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

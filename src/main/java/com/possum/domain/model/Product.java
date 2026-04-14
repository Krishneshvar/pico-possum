package com.possum.domain.model;

import java.time.LocalDateTime;

public record Product(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        String sku,
        java.math.BigDecimal mrp,
        java.math.BigDecimal costPrice,
        Integer stockAlertCap,
        String status,
        String imagePath,
        Integer stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

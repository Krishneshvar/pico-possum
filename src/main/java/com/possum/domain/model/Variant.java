package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Variant(
        Long id,
        Long productId,
        String productName,
        String name,
        String sku,
        BigDecimal price,
        BigDecimal costPrice,
        Integer stockAlertCap,
        Boolean defaultVariant,
        String status,
        String imagePath,
        Integer stock,
        String categoryName,
        String taxCategoryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

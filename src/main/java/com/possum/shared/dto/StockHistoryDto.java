package com.possum.shared.dto;

import java.time.LocalDateTime;

public record StockHistoryDto(
        Long id,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        Integer quantityChange,
        String reason,
        String adjustedByName,
        LocalDateTime adjustedAt
) {}

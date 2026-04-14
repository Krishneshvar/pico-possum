package com.possum.shared.dto;

import java.time.LocalDateTime;

public record StockHistoryDto(
        Long id,
        Long productId,
        String productName,
        String sku,
        Integer quantityChange,
        String reason,
        String adjustedByName,
        LocalDateTime adjustedAt
) {}

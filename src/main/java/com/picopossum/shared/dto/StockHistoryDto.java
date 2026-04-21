package com.picopossum.shared.dto;

import java.time.LocalDateTime;

/**
 * DTO for stock history visualization.
 * Minimalist version for Single-User SMB.
 */
public record StockHistoryDto(
        Long id,
        Long productId,
        String productName,
        String sku,
        Integer quantityChange,
        String reason,
        LocalDateTime createdAt,
        Integer currentStock,
        Integer stockAlertCap
) {}

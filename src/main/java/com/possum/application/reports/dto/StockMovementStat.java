package com.possum.application.reports.dto;

public record StockMovementStat(
        String productName,
        String variantName,
        String sku,
        int incoming,
        int outgoing,
        int returns,
        int adjustments,
        int currentStock
) {
}

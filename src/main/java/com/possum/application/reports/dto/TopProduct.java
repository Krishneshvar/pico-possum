package com.possum.application.reports.dto;

import java.math.BigDecimal;

public record TopProduct(
        long productId,
        String productName,
        String variantName,
        String sku,
        int totalQuantitySold,
        BigDecimal totalRevenue
) {
}

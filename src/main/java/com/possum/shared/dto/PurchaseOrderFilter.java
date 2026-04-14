package com.possum.shared.dto;

import java.util.List;

public record PurchaseOrderFilter(
        int page,
        int limit,
        String searchTerm,
        List<String> statuses,
        String fromDate,
        String toDate,
        String sortBy,
        String sortOrder,
        List<Long> paymentMethodIds,
        java.math.BigDecimal minPrice,
        java.math.BigDecimal maxPrice
) {
}

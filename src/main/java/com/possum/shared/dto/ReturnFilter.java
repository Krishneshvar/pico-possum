package com.possum.shared.dto;

import java.util.List;

public record ReturnFilter(
        Long saleId,
        Long userId,
        String startDate,
        String endDate,
        java.math.BigDecimal minAmount,
        java.math.BigDecimal maxAmount,
        List<Long> paymentMethodIds,
        String searchTerm,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder
) {
}

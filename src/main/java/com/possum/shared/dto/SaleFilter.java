package com.possum.shared.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaleFilter(
        List<String> status,
        Long customerId,
        Long userId,
        String startDate,
        String endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String searchTerm,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder,
        List<String> fulfillmentStatus,
        List<Long> paymentMethodIds
) {
}

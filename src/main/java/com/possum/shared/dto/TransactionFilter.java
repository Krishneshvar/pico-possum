package com.possum.shared.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionFilter(
        String startDate,
        String endDate,
        List<String> type,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        List<Long> paymentMethodId,
        String status,
        String searchTerm,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder
) {
}

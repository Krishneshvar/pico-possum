package com.possum.shared.dto;

import java.util.List;

public record SupplierFilter(
        int page,
        int limit,
        String searchTerm,
        List<Long> paymentPolicyIds,
        String sortBy,
        String sortOrder
) {
}

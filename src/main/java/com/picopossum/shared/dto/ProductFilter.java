package com.picopossum.shared.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductFilter(
        String searchTerm,
        List<String> status,
        List<Long> categories,
        List<String> stockStatuses,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder
) {
    public ProductFilter(
            String searchTerm,
            List<String> status,
            List<Long> categories,
            int currentPage,
            int itemsPerPage,
            String sortBy,
            String sortOrder
    ) {
        this(searchTerm, status, categories, null, null, null, currentPage, itemsPerPage, sortBy, sortOrder);
    }
}

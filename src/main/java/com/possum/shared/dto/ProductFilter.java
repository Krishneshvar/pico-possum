package com.possum.shared.dto;

import java.util.List;

public record ProductFilter(
        String searchTerm,
        List<Long> taxCategories,
        List<String> status,
        List<Long> categories,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder
) {
}

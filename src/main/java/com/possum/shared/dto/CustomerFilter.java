package com.possum.shared.dto;

public record CustomerFilter(
        String searchTerm,
        Integer page,
        Integer limit,
        int currentPage,
        int itemsPerPage,
        String sortBy,
        String sortOrder
) {
}

package com.possum.shared.dto;

public record AuditLogFilter(
        String tableName,
        Long rowId,
        Long userId,
        java.util.List<String> actions,
        String startDate,
        String endDate,
        String searchTerm,
        String sortBy,
        String sortOrder,
        int currentPage,
        int itemsPerPage
) {
}

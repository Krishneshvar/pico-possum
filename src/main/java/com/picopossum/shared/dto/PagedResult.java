package com.picopossum.shared.dto;

import java.util.List;

public record PagedResult<T>(
        List<T> items,
        int totalCount,
        int totalPages,
        int page,
        int limit
) {
}

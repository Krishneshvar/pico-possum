package com.picopossum.shared.dto;

import java.util.List;

public record UserFilter(
        String searchTerm,
        Integer page,
        Integer limit,
        List<Boolean> activeStatuses,
        Boolean active
) {
}

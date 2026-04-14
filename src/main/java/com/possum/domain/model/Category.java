package com.possum.domain.model;

import java.time.LocalDateTime;

public record Category(
        Long id,
        String name,
        Long parentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

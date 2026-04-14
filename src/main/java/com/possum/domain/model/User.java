package com.possum.domain.model;

import java.time.LocalDateTime;

public record User(
        Long id,
        String name,
        String username,
        String passwordHash,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}

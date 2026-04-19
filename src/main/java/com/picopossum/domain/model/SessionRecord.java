package com.picopossum.domain.model;

public record SessionRecord(
        String id,
        Long userId,
        String token,
        Long expiresAt,
        String data
) {
}

package com.possum.domain.model;

import java.time.LocalDateTime;

public record AuditLog(
        Long id,
        Long userId,
        String action,
        String tableName,
        Long rowId,
        String oldData,
        String newData,
        String eventDetails,
        String userName,
        LocalDateTime createdAt
) {
}

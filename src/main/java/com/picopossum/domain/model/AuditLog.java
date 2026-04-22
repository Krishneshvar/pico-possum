package com.picopossum.domain.model;

import java.time.LocalDateTime;

/**
 * Minimalist Audit log for Single-User SMB.
 * Removed userId/userName as they are redundant in a one-person system.
 */
public record AuditLog(
        Long id,
        String action,
        String tableName,
        Long rowId,
        String oldData,
        String newData,
        String eventDetails,
        String severity,
        LocalDateTime createdAt,
        String integrityHash
) {
}

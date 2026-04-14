package com.possum.domain.model;

public record UserPermissionOverride(
        Long userId,
        Long permissionId,
        String permissionKey,
        Boolean granted
) {
}

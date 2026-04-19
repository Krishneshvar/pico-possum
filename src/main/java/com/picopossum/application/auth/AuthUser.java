package com.picopossum.application.auth;

public record AuthUser(
        long id,
        String name,
        String username
) {
}

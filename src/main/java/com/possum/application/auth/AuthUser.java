package com.possum.application.auth;

import java.util.List;

public record AuthUser(
        long id,
        String name,
        String username,
        List<String> roles,
        List<String> permissions
) {
}

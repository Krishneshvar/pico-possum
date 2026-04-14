package com.possum.application.auth;

import java.util.List;

public record UserContext(
        long id,
        List<String> roles,
        List<String> permissions
) {
}

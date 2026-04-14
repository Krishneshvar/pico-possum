package com.possum.application.auth.handlers;

public record LoginRequest(
        String username,
        String password
) {
}

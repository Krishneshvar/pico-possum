package com.picopossum.application.auth.handlers;

public record LoginRequest(
        String username,
        String password
) {
}

package com.possum.application.auth.handlers;

import com.possum.application.auth.AuthService;
import com.possum.application.auth.AuthUser;
import com.possum.application.auth.LoginResponse;
import com.possum.domain.exceptions.AuthenticationException;

public class AuthHandler {

    private final AuthService authService;

    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    public LoginResponse login(LoginRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new AuthenticationException("Username and password are required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new AuthenticationException("Username and password are required");
        }

        return authService.login(request.username(), request.password());
    }

    public AuthUser me(long userId) {
        return authService.getCurrentUser(userId);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
    }
}

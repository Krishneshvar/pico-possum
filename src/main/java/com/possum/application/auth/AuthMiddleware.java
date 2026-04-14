package com.possum.application.auth;

import com.possum.domain.exceptions.AuthenticationException;
import com.possum.domain.exceptions.AuthorizationException;

import java.util.List;

public class AuthMiddleware {

    private final AuthService authService;
    private final AuthorizationService authorizationService;

    public AuthMiddleware(AuthService authService, AuthorizationService authorizationService) {
        this.authService = authService;
        this.authorizationService = authorizationService;
    }

    public AuthUser authenticate(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new AuthenticationException("Unauthorized: No token provided");
        }

        String[] parts = authHeader.split(" ");
        if (parts.length != 2 || !"Bearer".equals(parts[0])) {
            throw new AuthenticationException("Unauthorized: Invalid token format");
        }

        String token = parts[1];
        AuthUser user = authService.validateSession(token);

        if (user == null) {
            throw new AuthenticationException("Unauthorized: Session expired or invalid");
        }

        return user;
    }

    public void requirePermission(AuthUser user, String permission) {
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());
        
        if (!authorizationService.hasPermission(context, permission)) {
            throw new AuthorizationException("Forbidden: Missing required permission (" + permission + ")");
        }
    }

    public void requireAnyPermission(AuthUser user, List<String> permissions) {
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());
        
        if (!authorizationService.hasAnyPermission(context, permissions)) {
            throw new AuthorizationException("Forbidden: Missing required permission (" + String.join(" OR ", permissions) + ")");
        }
    }

    public void requireRole(AuthUser user, String role) {
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());
        
        if (!authorizationService.hasRole(context, role)) {
            throw new AuthorizationException("Forbidden: Missing required role (" + role + ")");
        }
    }

    public void requireAnyRole(AuthUser user, List<String> roles) {
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());
        
        if (!authorizationService.hasAnyRole(context, roles)) {
            throw new AuthorizationException("Forbidden: Missing required role (" + String.join(" OR ", roles) + ")");
        }
    }

    public void requireAdmin(AuthUser user) {
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());
        
        if (!authorizationService.isAdmin(context)) {
            throw new AuthorizationException("Forbidden: Admin access required");
        }
    }
}

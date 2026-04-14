package com.possum.application.auth;

import com.possum.application.auth.handlers.AuthHandler;
import com.possum.application.auth.handlers.LoginRequest;
import com.possum.domain.exceptions.AuthenticationException;
import com.possum.domain.exceptions.AuthorizationException;

import java.util.List;

/**
 * Usage examples for the authentication and authorization system.
 * This class demonstrates how to use the auth components in controllers/handlers.
 */
public class AuthUsageExamples {

    private final AuthModule authModule;

    public AuthUsageExamples(AuthModule authModule) {
        this.authModule = authModule;
    }

    // Example 1: Login
    public void loginExample() {
        AuthHandler handler = authModule.getAuthHandler();
        LoginRequest request = new LoginRequest("admin", "password123");

        try {
            LoginResponse response = handler.login(request);
            System.out.println("Logged in: " + response.user().username());
            System.out.println("Token: " + response.token());
        } catch (AuthenticationException e) {
            System.err.println("Login failed: " + e.getMessage());
        }
    }

    // Example 2: Authenticate request with Bearer token
    public void authenticateRequestExample(String authorizationHeader) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            AuthUser user = middleware.authenticate(authorizationHeader);
            AuthContext.setCurrentUser(user);
            System.out.println("Authenticated: " + user.username());
        } catch (AuthenticationException e) {
            System.err.println("Authentication failed: " + e.getMessage());
        } finally {
            AuthContext.clear();
        }
    }

    // Example 3: Check single permission
    public void checkPermissionExample(AuthUser user) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            middleware.requirePermission(user, Permissions.PRODUCTS_VIEW);
            System.out.println("User has products.view permission");
        } catch (AuthorizationException e) {
            System.err.println("Permission denied: " + e.getMessage());
        }
    }

    // Example 4: Check any permission (OR logic)
    public void checkAnyPermissionExample(AuthUser user) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            middleware.requireAnyPermission(user, List.of(
                    Permissions.SALES_CREATE,
                    Permissions.SALES_MANAGE
            ));
            System.out.println("User can create or manage sales");
        } catch (AuthorizationException e) {
            System.err.println("Permission denied: " + e.getMessage());
        }
    }

    // Example 5: Check admin role
    public void checkAdminExample(AuthUser user) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            middleware.requireAdmin(user);
            System.out.println("User is admin");
        } catch (AuthorizationException e) {
            System.err.println("Admin access required: " + e.getMessage());
        }
    }

    // Example 6: Check role
    public void checkRoleExample(AuthUser user) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            middleware.requireAnyRole(user, List.of(Roles.MANAGER, Roles.ADMIN));
            System.out.println("User is manager or admin");
        } catch (AuthorizationException e) {
            System.err.println("Role check failed: " + e.getMessage());
        }
    }

    // Example 7: Logout
    public void logoutExample(String token) {
        AuthHandler handler = authModule.getAuthHandler();
        handler.logout(token);
        System.out.println("Logged out successfully");
    }

    // Example 8: Get current user info
    public void getCurrentUserExample(long userId) {
        AuthHandler handler = authModule.getAuthHandler();
        AuthUser user = handler.me(userId);
        System.out.println("Current user: " + user.username());
        System.out.println("Roles: " + user.roles());
        System.out.println("Permissions: " + user.permissions());
    }

    // Example 9: Programmatic permission check (without throwing exception)
    public void programmaticPermissionCheckExample(AuthUser user) {
        AuthorizationService authz = authModule.getAuthorizationService();
        UserContext context = new UserContext(user.id(), user.roles(), user.permissions());

        if (authz.hasPermission(context, Permissions.SALES_REFUND)) {
            System.out.println("User can process refunds");
        } else {
            System.out.println("User cannot process refunds");
        }
    }

    // Example 10: Complete request flow
    public void completeRequestFlowExample(String authorizationHeader) {
        AuthMiddleware middleware = authModule.getAuthMiddleware();

        try {
            // 1. Authenticate
            AuthUser user = middleware.authenticate(authorizationHeader);
            AuthContext.setCurrentUser(user);

            // 2. Check permission
            middleware.requirePermission(user, Permissions.PRODUCTS_MANAGE);

            // 3. Execute business logic
            System.out.println("Processing product management request...");

        } catch (AuthenticationException e) {
            System.err.println("401 Unauthorized: " + e.getMessage());
        } catch (AuthorizationException e) {
            System.err.println("403 Forbidden: " + e.getMessage());
        } finally {
            AuthContext.clear();
        }
    }
}

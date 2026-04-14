package com.possum.ui.navigation;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.auth.AuthorizationService;
import com.possum.application.auth.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteGuard.class);

    private final AuthorizationService authorizationService;

    public RouteGuard(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean canAccess(RouteDefinition route) {
        AuthUser currentUser = AuthContext.getCurrentUser();

        if (currentUser == null) {
            LOGGER.warn("Access denied to route '{}': No authenticated user", route.getRouteId());
            return false;
        }

        if (!route.hasPermissionRequirements()) {
            return true;
        }

        UserContext userContext = new UserContext(
            currentUser.id(),
            currentUser.roles(),
            currentUser.permissions()
        );

        boolean hasAccess = authorizationService.hasAnyPermission(
            userContext,
            route.getRequiredPermissions()
        );

        if (!hasAccess) {
            LOGGER.warn("Access denied to route '{}' for user '{}': Missing required permissions {}",
                route.getRouteId(), currentUser.username(), route.getRequiredPermissions());
        }

        return hasAccess;
    }

    public boolean isAuthenticated() {
        return AuthContext.getCurrentUser() != null;
    }
}

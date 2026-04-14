package com.possum.ui.navigation;

import java.util.List;

public class RouteDefinition {
    private final String routeId;
    private final String fxmlPath;
    private final List<String> requiredPermissions;
    private final boolean requiresAuth;

    public RouteDefinition(String routeId, String fxmlPath) {
        this(routeId, fxmlPath, List.of(), true);
    }

    public RouteDefinition(String routeId, String fxmlPath, List<String> requiredPermissions) {
        this(routeId, fxmlPath, requiredPermissions, true);
    }

    public RouteDefinition(String routeId, String fxmlPath, List<String> requiredPermissions, boolean requiresAuth) {
        this.routeId = routeId;
        this.fxmlPath = fxmlPath;
        this.requiredPermissions = requiredPermissions;
        this.requiresAuth = requiresAuth;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }

    public List<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }

    public boolean hasPermissionRequirements() {
        return !requiredPermissions.isEmpty();
    }
}

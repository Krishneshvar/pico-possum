package com.picopossum.ui.navigation;

import java.util.List;

public class RouteDefinition {
    private final String routeId;
    private final String fxmlPath;
    private final boolean requiresAuth;

    public RouteDefinition(String routeId, String fxmlPath) {
        this(routeId, fxmlPath, true);
    }

    public RouteDefinition(String routeId, String fxmlPath, boolean requiresAuth) {
        this.routeId = routeId;
        this.fxmlPath = fxmlPath;
        this.requiresAuth = requiresAuth;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }

    public boolean requiresAuth() {
        return requiresAuth;
    }
}

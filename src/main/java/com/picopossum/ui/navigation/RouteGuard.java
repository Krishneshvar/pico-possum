package com.picopossum.ui.navigation;

import com.picopossum.application.auth.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RouteGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteGuard.class);

    public RouteGuard() {
    }

    public boolean canAccess(RouteDefinition route) {
        return AuthContext.getCurrentUser() != null;
    }

    public boolean isAuthenticated() {
        return AuthContext.getCurrentUser() != null;
    }
}

package com.picopossum.ui.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.picopossum.ui.common.dialogs.DialogStyler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NavigationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavigationManager.class);

    private final StackPane contentContainer;
    private final RouteRegistry routeRegistry;
    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();
    private final RouteGuard routeGuard;
    private final List<Consumer<String>> navigationListeners = new ArrayList<>();
    private String currentRoute;
    private com.picopossum.ui.DependencyInjector dependencyInjector;

    public void setDependencyInjector(com.picopossum.ui.DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
    }

    public NavigationManager(StackPane contentContainer, RouteGuard routeGuard) {
        this.contentContainer = contentContainer;
        this.routeGuard = routeGuard;
        this.routeRegistry = new RouteRegistry();
    }

    public void navigateTo(String routeId) {
        navigateTo(routeId, null);
    }

    public void navigateTo(String routeId, Map<String, Object> params) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        if (route == null) {
            LOGGER.error("Route not found: {}", routeId);
            return;
        }

        if (route.requiresAuth() && !routeGuard.isAuthenticated()) {
            LOGGER.warn("Unauthenticated access attempt to route: {}", routeId);
            navigateTo("dashboard"); // Or login, but keep it consistent for now
            return;
        }

        try {
            Node view = getOrLoadView(routeId, route.getFxmlPath(), params);
            contentContainer.getChildren().clear();
            contentContainer.getChildren().add(view);
            currentRoute = routeId;
            LOGGER.debug("Navigated to: {}", routeId);
            notifyNavigationListeners(routeId);
        } catch (IOException e) {
            LOGGER.error("Failed to load view for route: {}", routeId, e);
            loadErrorView(routeId);
        }
    }

    private Node getOrLoadView(String routeId, String fxmlPath, Map<String, Object> params) throws IOException {
        Node cachedView = viewCache.get(routeId);
        if (cachedView != null) {
            LOGGER.debug("Using cached view for: {}", routeId);
            if (params != null) {
                Object controller = controllerCache.get(routeId);
                if (controller instanceof Parameterizable) {
                    ((Parameterizable) controller).setParameters(params);
                }
            }
            return cachedView;
        }

        LOGGER.debug("Loading view for: {}", routeId);
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        if (dependencyInjector != null) {
            loader.setControllerFactory(dependencyInjector.getControllerFactory());
        }
        Node view = loader.load();
        
        Object controller = loader.getController();
        if (controller != null) {
            controllerCache.put(routeId, controller);
            if (params != null && controller instanceof Parameterizable) {
                ((Parameterizable) controller).setParameters(params);
            }
        }
        
        viewCache.put(routeId, view);
        return view;
    }

    private void loadErrorView(String routeId) {
        contentContainer.getChildren().clear();
        javafx.scene.control.Label errorLabel = new javafx.scene.control.Label(
            "Failed to load view: " + routeId
        );
        errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #d32f2f;");
        contentContainer.getChildren().add(errorLabel);
    }

    public void addNavigationListener(Consumer<String> listener) {
        navigationListeners.add(listener);
    }

    private void notifyNavigationListeners(String routeId) {
        for (Consumer<String> listener : navigationListeners) {
            listener.accept(routeId);
        }
    }

    public boolean canAccessRoute(String routeId) {
        RouteDefinition route = routeRegistry.getRoute(routeId);
        return route != null && routeGuard.canAccess(route);
    }

    public String getCurrentRoute() {
        return currentRoute;
    }

    public Object getController(String routeId) {
        return controllerCache.get(routeId);
    }

    public void clearCache() {
        viewCache.clear();
        controllerCache.clear();
    }
}

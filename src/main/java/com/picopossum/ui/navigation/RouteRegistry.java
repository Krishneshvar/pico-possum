package com.picopossum.ui.navigation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteRegistry {

    private final Map<String, RouteDefinition> routes = new HashMap<>();

    public RouteRegistry() {
        registerAllRoutes();
    }

    private void registerAllRoutes() {
        // Dashboard
        register("dashboard", "/fxml/dashboard/dashboard-view.fxml");

        // Products & Inventory
        register("products", "/fxml/products/products-view.fxml");
        register("product-form", "/fxml/products/product-form-view.fxml");
        register("inventory", "/fxml/inventory/inventory-view.fxml");
        register("categories", "/fxml/categories/categories-view.fxml");
        register("stock-history", "/fxml/inventory/stock-history-view.fxml");

        // Sales & Commercial
        register("sales", "/fxml/sales/pos-view.fxml");
        register("sales-history", "/fxml/sales/sales-history-view.fxml");
        register("returns", "/fxml/returns/returns-view.fxml");

        // People
        register("users", "/fxml/people/users-view.fxml");
        register("customers", "/fxml/people/customers-view.fxml");

        // Reports & Insights
        register("reports-sales", "/fxml/reports/sales-reports-view.fxml");
        register("reports-analytics", "/fxml/reports/sales-analytics-view.fxml");
        register("business-insights", "/fxml/insights/business-insights-view.fxml");
        register("product-flow", "/fxml/insights/product-flow-view.fxml");
        register("audit-log", "/fxml/audit/audit-view.fxml");

        // System
        register("settings", "/fxml/settings/settings-view.fxml");
        register("component-demo", "/fxml/design/component-demo-view.fxml");
    }

    private void register(String routeId, String fxmlPath) {
        routes.put(routeId, new RouteDefinition(routeId, fxmlPath));
    }

    public RouteDefinition getRoute(String routeId) {
        return routes.get(routeId);
    }

    public Map<String, RouteDefinition> getAllRoutes() {
        return new HashMap<>(routes);
    }

    public boolean hasRoute(String routeId) {
        return routes.containsKey(routeId);
    }
}

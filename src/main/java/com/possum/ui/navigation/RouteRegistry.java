package com.possum.ui.navigation;

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
        register("products", "/fxml/products/products-view.fxml", "products.view");
        register("product-form", "/fxml/products/product-form-view.fxml", "products.manage");
        register("inventory", "/fxml/inventory/inventory-view.fxml", "inventory.view");
        register("variants", "/fxml/inventory/variants-view.fxml", "inventory.view");
        register("categories", "/fxml/categories/categories-view.fxml", "categories.view");
        register("stock-history", "/fxml/inventory/stock-history-view.fxml", "inventory.view");

        // Sales & Commercial
        register("sales", "/fxml/sales/pos-view.fxml", "sales.create");
        register("sales-history", "/fxml/sales/sales-history-view.fxml", "sales.view");
        register("transactions", "/fxml/transactions/transactions-view.fxml", "transactions.view");
        register("returns", "/fxml/returns/returns-view.fxml", "returns.view");

        // Purchase
        register("purchase", "/fxml/purchase/purchase-view.fxml", "purchase.view");
        register("suppliers", "/fxml/purchase/suppliers-view.fxml", "suppliers.view");
        register("payment-policies", "/fxml/purchase/payment-policies-view.fxml", "suppliers.view");
        register("payment-policy-form", "/fxml/purchase/payment-policy-form-view.fxml", "suppliers.manage");

        // People
        register("users", "/fxml/people/users-view.fxml", "users.view");
        register("customers", "/fxml/people/customers-view.fxml", "customers.view");

        // Reports & Insights
        register("reports-sales", "/fxml/reports/sales-reports-view.fxml", "reports.view");
        register("reports-analytics", "/fxml/reports/sales-analytics-view.fxml", "reports.view");
        register("business-insights", "/fxml/insights/business-insights-view.fxml", "reports.view");
        register("product-flow", "/fxml/insights/product-flow-view.fxml", "reports.view");
        register("audit-log", "/fxml/audit/audit-view.fxml", "audit.view");

        // System
        register("settings", "/fxml/settings/settings-view.fxml", "settings.view");
        register("component-demo", "/fxml/design/component-demo-view.fxml", "settings.view");
    }

    private void register(String routeId, String fxmlPath) {
        routes.put(routeId, new RouteDefinition(routeId, fxmlPath));
    }

    private void register(String routeId, String fxmlPath, String... permissions) {
        routes.put(routeId, new RouteDefinition(routeId, fxmlPath, List.of(permissions)));
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

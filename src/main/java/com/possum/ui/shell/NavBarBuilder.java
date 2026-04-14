package com.possum.ui.shell;

import com.possum.ui.workspace.WorkspaceManager;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Responsible solely for building and populating the navigation bar.
 * Reads route definitions and builds the nav buttons/menus,
 * delegating open/focus calls to WorkspaceManager.
 */
public class NavBarBuilder {

    private final HBox navItems;
    private final WorkspaceManager workspaceManager;

    public NavBarBuilder(HBox navItems, WorkspaceManager workspaceManager) {
        this.navItems = navItems;
        this.workspaceManager = workspaceManager;
    }

    public void build() {
        createNavButton("Dashboard", "bx-home", "Dashboard", "/fxml/dashboard/dashboard-view.fxml", null);
        createNavMenu("Inventory", "bx-package", new Object[][]{
            {"Products",    "/fxml/products/products-view.fxml",         com.possum.application.auth.Permissions.PRODUCTS_VIEW},
            {"Variants",    "/fxml/inventory/variants-view.fxml",        com.possum.application.auth.Permissions.INVENTORY_VIEW},
            {"Categories",  "/fxml/categories/categories-view.fxml",     com.possum.application.auth.Permissions.CATEGORIES_VIEW},
            {"Stock",       "/fxml/inventory/inventory-view.fxml",       com.possum.application.auth.Permissions.INVENTORY_VIEW},
            {"Stock History","/fxml/inventory/stock-history-view.fxml",  com.possum.application.auth.Permissions.INVENTORY_VIEW}
        });
        createNavMenu("Sales", "bx-cart", new Object[][]{
            {"Point of Sale", "/fxml/sales/pos-view.fxml",              com.possum.application.auth.Permissions.SALES_CREATE},
            {"Bill History",  "/fxml/sales/sales-history-view.fxml",    com.possum.application.auth.Permissions.SALES_VIEW},
            {"Transactions",  "/fxml/transactions/transactions-view.fxml", com.possum.application.auth.Permissions.TRANSACTIONS_VIEW},
            {"Returns",       "/fxml/returns/returns-view.fxml",        com.possum.application.auth.Permissions.RETURNS_VIEW}
        });
        createNavMenu("Purchase", "bx-purchase-tag", new Object[][]{
            {"Suppliers",       "/fxml/purchase/suppliers-view.fxml",  com.possum.application.auth.Permissions.SUPPLIERS_VIEW},
            {"Payment Policies", "/fxml/purchase/payment-policies-view.fxml", com.possum.application.auth.Permissions.SUPPLIERS_VIEW},
            {"Purchase Orders", "/fxml/purchase/purchase-view.fxml",   com.possum.application.auth.Permissions.PURCHASE_VIEW}
        });
        createNavMenu("People", "bx-group", new Object[][]{
            {"Employees", "/fxml/people/users-view.fxml",     com.possum.application.auth.Permissions.USERS_VIEW},
            {"Customers", "/fxml/people/customers-view.fxml", com.possum.application.auth.Permissions.CUSTOMERS_VIEW}
        });
        createNavMenu("Insights", "bx-bar-chart-alt-2", new Object[][]{
            {"Sales Reports",   "/fxml/reports/sales-reports-view.fxml",   com.possum.application.auth.Permissions.REPORTS_VIEW},
            {"Sales Analytics", "/fxml/reports/sales-analytics-view.fxml", com.possum.application.auth.Permissions.REPORTS_VIEW},
            {"Business Insights", "/fxml/insights/business-insights-view.fxml", com.possum.application.auth.Permissions.REPORTS_VIEW},
            {"Product Flow",    "/fxml/insights/product-flow-view.fxml",   com.possum.application.auth.Permissions.REPORTS_VIEW},
            {"Audit Log",       "/fxml/audit/audit-view.fxml",             com.possum.application.auth.Permissions.AUDIT_VIEW}
        });
        createNavButton("Settings", "bx-cog", "Settings", "/fxml/settings/settings-view.fxml",
                com.possum.application.auth.Permissions.SETTINGS_VIEW);
    }

    private void createNavButton(String label, String iconName, String title, String fxmlPath, String permission) {
        if (permission != null && !com.possum.ui.common.UIPermissionUtil.hasPermission(permission)) {
            return;
        }
        Button btn = new Button(label);
        FontIcon icon = new FontIcon(iconName);
        icon.getStyleClass().add("nav-icon");
        btn.setGraphic(icon);
        btn.setGraphicTextGap(8);
        btn.getStyleClass().add("nav-menu-btn");
        btn.setOnAction(e -> workspaceManager.openOrFocusWindow(title, fxmlPath));
        btn.setAccessibleText(label);
        btn.setTooltip(new Tooltip(label));
        HBox.setMargin(btn, new Insets(0, 4, 0, 4));
        navItems.getChildren().add(btn);
    }

    private void createNavMenu(String label, String iconName, Object[][] items) {
        MenuButton menuBtn = new MenuButton(label);
        FontIcon icon = new FontIcon(iconName);
        icon.getStyleClass().add("nav-icon");
        menuBtn.setGraphic(icon);
        menuBtn.setGraphicTextGap(8);
        menuBtn.getStyleClass().add("nav-menu-btn");
        menuBtn.setAccessibleText(label);
        menuBtn.setTooltip(new Tooltip(label));
        HBox.setMargin(menuBtn, new Insets(0, 4, 0, 4));

        int added = 0;
        for (Object[] item : items) {
            String itemLabel = (String) item[0];
            String fxmlPath  = (String) item[1];
            String permission = item.length > 2 ? (String) item[2] : null;
            if (permission == null || com.possum.ui.common.UIPermissionUtil.hasPermission(permission)) {
                MenuItem mi = new MenuItem(itemLabel);
                mi.setOnAction(e -> workspaceManager.openOrFocusWindow(itemLabel, fxmlPath));
                menuBtn.getItems().add(mi);
                added++;
            }
        }
        if (added > 0) {
            navItems.getChildren().add(menuBtn);
        }
    }
}

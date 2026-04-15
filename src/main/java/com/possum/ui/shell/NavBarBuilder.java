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
        createNavButton("Dashboard", "bx-home", "Dashboard", "/fxml/dashboard/dashboard-view.fxml");
        createNavMenu("Inventory", "bx-package", new Object[][]{
            {"Products",    "/fxml/products/products-view.fxml"},
            {"Categories",  "/fxml/categories/categories-view.fxml"},
            {"Stock",       "/fxml/inventory/inventory-view.fxml"},
            {"Stock Activity","/fxml/inventory/stock-history-view.fxml"}
        });
        createNavMenu("Commercial", "bx-cart", new Object[][]{
            {"Point of Sale", "/fxml/sales/pos-view.fxml"},
            {"Bill History",  "/fxml/sales/sales-history-view.fxml"},
            {"Transactions",  "/fxml/transactions/transactions-view.fxml"},
            {"Customers",     "/fxml/people/customers-view.fxml"},
            {"Returns",       "/fxml/returns/returns-view.fxml"}
        });
        createNavMenu("Insights", "bx-bar-chart-alt-2", new Object[][]{
            {"Sales Reports",   "/fxml/reports/sales-reports-view.fxml"},
            {"Sales Analytics", "/fxml/reports/sales-analytics-view.fxml"},
            {"Business Insights", "/fxml/insights/business-insights-view.fxml"},
            {"Product Flow",    "/fxml/insights/product-flow-view.fxml"},
            {"Audit Log",       "/fxml/audit/audit-view.fxml"}
        });
        createNavButton("Settings", "bx-cog", "Settings", "/fxml/settings/settings-view.fxml");
    }

    private void createNavButton(String label, String iconName, String title, String fxmlPath) {
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

        for (Object[] item : items) {
            String itemLabel = (String) item[0];
            String fxmlPath  = (String) item[1];
            MenuItem mi = new MenuItem(itemLabel);
            mi.setOnAction(e -> workspaceManager.openOrFocusWindow(itemLabel, fxmlPath));
            menuBtn.getItems().add(mi);
        }
        navItems.getChildren().add(menuBtn);
    }
}

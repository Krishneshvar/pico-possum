package com.possum.ui;


import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.ui.navigation.NavigationManager;
import com.possum.ui.navigation.RouteGuard;
import com.possum.ui.shell.GlobalShortcutHandler;
import com.possum.ui.shell.NavBarBuilder;
import com.possum.ui.shell.UserMenuManager;
import com.possum.ui.workspace.WorkspaceDesktop;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.common.accessibility.AccessibilityEnhancer;
import com.possum.ui.common.controls.NotificationService;
import com.possum.application.auth.AuthContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

public class AppShellController {

    private DependencyInjector dependencyInjector;

    public AppShellController(DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
    }

    public AppShellController() {}

    @FXML private HBox navbar;
    @FXML private HBox brandBox;
    @FXML private HBox navItems;
    @FXML private StackPane contentArea;
    @FXML private Label userAvatar;
    @FXML private MenuButton userMenuButton;
    @FXML private ImageView brandIcon;

    private NavigationManager navigationManager;
    private WorkspaceManager workspaceManager;

    public void setDependencyInjector(DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
        if (workspaceManager != null) {
            workspaceManager.setDependencyInjector(dependencyInjector);
            dependencyInjector.setWorkspaceManager(workspaceManager);
        }
        if (navigationManager != null) {
            navigationManager.setDependencyInjector(dependencyInjector);
            dependencyInjector.setNavigationManager(navigationManager);
        }
    }

    @FXML
    public void initialize() {
        String currentUserName = "Admin User";
        var currentUser = AuthContext.getCurrentUser();
        if (currentUser != null) currentUserName = currentUser.name();

        // Workspace
        WorkspaceDesktop desktop = new WorkspaceDesktop();
        workspaceManager = new WorkspaceManager(desktop, dependencyInjector);
        if (dependencyInjector != null) {
            dependencyInjector.setWorkspaceManager(workspaceManager);
        }
        contentArea.getChildren().add(desktop);
        desktop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        NotificationService.initialize(contentArea);

        // Navigation
        RouteGuard routeGuard = new RouteGuard(new com.possum.application.auth.AuthorizationService());
        navigationManager = new NavigationManager(contentArea, routeGuard);
        new NavBarBuilder(navItems, workspaceManager).build();

        // User menu
        new UserMenuManager(userMenuButton, userAvatar, dependencyInjector, currentUserName).build();

        // Brand icon
        loadBrandIcon();
        setupBrandBox(currentUserName);

        // Keyboard shortcuts
        new GlobalShortcutHandler(contentArea, workspaceManager).install();

        // Accessibility
        Platform.runLater(() -> {
            if (contentArea.getScene() != null) {
                AccessibilityEnhancer.enhance(contentArea.getScene().getRoot());
            }
        });
    }

    private void loadBrandIcon() {
        try {
            String iconPath = getClass().getResource("/icons/icon-shell.png").toExternalForm();
            brandIcon.setImage(new javafx.scene.image.Image(iconPath, 28, 28, true, true, true));
        } catch (Exception e) {
            LoggingConfig.getLogger().warn("Failed to load brand icon: {}", e.getMessage());
        }
    }

    private void setupBrandBox(String currentUserName) {
        if (brandBox == null) return;
        Tooltip.install(brandBox, new Tooltip("Go to POSSUM Dashboard"));
        brandBox.setAccessibleRole(javafx.scene.AccessibleRole.BUTTON);
        brandBox.setAccessibleText("POSSUM Dashboard");
        brandBox.setFocusTraversable(true);
        brandBox.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                workspaceManager.openOrFocusWindow("Dashboard", "/fxml/dashboard/dashboard-view.fxml");
            }
        });
        brandBox.setOnMouseClicked(e ->
            workspaceManager.openOrFocusWindow("Dashboard", "/fxml/dashboard/dashboard-view.fxml")
        );
    }

    // ── Public API used by external callers ──────────────────────────────────

    public void loadContent(Node content) {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(content);
    }

    public void setPageTitle(String title) { /* not needed with navbar */ }

    public StackPane getContentArea() { return contentArea; }

    public void setNavigationManager(NavigationManager navigationManager) {
        this.navigationManager = navigationManager;
    }

    public NavigationManager getNavigationManager() { return navigationManager; }
}

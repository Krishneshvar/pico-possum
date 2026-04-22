package com.picopossum.ui;


import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.ui.navigation.NavigationManager;
import com.picopossum.ui.navigation.RouteGuard;
import com.picopossum.ui.shell.GlobalShortcutHandler;
import com.picopossum.ui.shell.NavBarBuilder;
import com.picopossum.ui.workspace.WorkspaceDesktop;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.common.accessibility.AccessibilityEnhancer;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.application.auth.AuthContext;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
        RouteGuard routeGuard = new RouteGuard();
        navigationManager = new NavigationManager(contentArea, routeGuard);
        new NavBarBuilder(navItems, workspaceManager).build();

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
        brandBox.setFocusTraversable(false);
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

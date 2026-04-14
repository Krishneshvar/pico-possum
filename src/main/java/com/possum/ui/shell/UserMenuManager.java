package com.possum.ui.shell;

import com.possum.AppBootstrap;
import com.possum.ui.DependencyInjector;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.ui.auth.SessionStore;
import com.possum.ui.navigation.NavigationManager;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.application.auth.AuthContext;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Builds the user dropdown menu and handles user-related actions:
 * logout and theme toggling. Decoupled from AppShellController.
 */
public class UserMenuManager {

    private final MenuButton userMenuButton;
    private final Label userAvatar;
    private final DependencyInjector dependencyInjector;
    private String currentUserName;
    private boolean isDarkTheme = false;

    public UserMenuManager(MenuButton userMenuButton, Label userAvatar,
                           DependencyInjector dependencyInjector, String userName) {
        this.userMenuButton = userMenuButton;
        this.userAvatar = userAvatar;
        this.dependencyInjector = dependencyInjector;
        this.currentUserName = userName;
    }

    public void build() {
        initializeAvatar();

        Label nameLabel = new Label(currentUserName);
        nameLabel.getStyleClass().add("user-menu-header-name");

        Label roleLabel = new Label("Administrator");
        roleLabel.getStyleClass().add("user-menu-header-role");

        VBox headerBox = new VBox(nameLabel, roleLabel);
        headerBox.setStyle("-fx-background-color: transparent;");

        CustomMenuItem headerItem = new CustomMenuItem(headerBox);
        headerItem.setHideOnClick(false);

        MenuItem themeItem = new MenuItem("Toggle Theme");
        themeItem.getStyleClass().add("menu-item");
        themeItem.setOnAction(e -> handleThemeToggle());

        MenuItem logoutItem = new MenuItem("Logout");
        logoutItem.getStyleClass().add("logout-menu-item");
        logoutItem.setOnAction(e -> handleLogout());

        userMenuButton.getItems().addAll(
                headerItem, new SeparatorMenuItem(),
                themeItem, new SeparatorMenuItem(),
                logoutItem
        );

        if (userMenuButton != null) {
            userMenuButton.setAccessibleRole(javafx.scene.AccessibleRole.MENU_BUTTON);
            userMenuButton.setAccessibleText("User options");
            userMenuButton.setTooltip(new Tooltip("User options"));
        }
    }

    public void setUserName(String userName) {
        this.currentUserName = userName;
        initializeAvatar();
    }

    private void initializeAvatar() {
        if (currentUserName != null && !currentUserName.isEmpty() && userAvatar != null) {
            userAvatar.setText(String.valueOf(currentUserName.charAt(0)).toUpperCase());
        }
    }

    private void handleThemeToggle() {
        isDarkTheme = !isDarkTheme;
        System.out.println("Theme toggled: " + (isDarkTheme ? "Dark" : "Light"));
    }

    private void handleLogout() {
        try {
            if (dependencyInjector == null) {
                com.possum.ui.common.controls.NotificationService.error("Logout failed: System internal state (DependencyInjector) is null.");
                return;
            }

            AuthContext.clear();
            new SessionStore(dependencyInjector.getAppPaths(), new JsonService()).clearSession();

            Stage stage = (Stage) userMenuButton.getScene().getWindow();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auth/login-view.fxml"));

            NavigationManager dummyNav = new NavigationManager(null, null) {
                @Override
                public void navigateTo(String routeId) {
                    if ("dashboard".equals(routeId)) {
                        AppBootstrap b = new AppBootstrap();
                        b.start(stage);
                    }
                }
            };

            SessionStore sessionStore = new SessionStore(dependencyInjector.getAppPaths(), new JsonService());
            com.possum.application.auth.AuthModule authModule = dependencyInjector.getApplicationModule().getAuthModule();

            loader.setControllerFactory(type -> {
                if (type.equals(com.possum.ui.auth.LoginController.class)) {
                    return new com.possum.ui.auth.LoginController(
                        authModule.getAuthService(),
                        dummyNav,
                        sessionStore,
                        dependencyInjector.getToastService()
                    );
                }
                return null;
            });

            Parent root = loader.load();
            Scene scene = new Scene(root, 1200, 720);
            stage.setTitle("POSSUM - Login");
            stage.setScene(scene);
            stage.centerOnScreen();
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to logout: {}", e.getMessage(), e);
            com.possum.ui.common.controls.NotificationService.error("Logout failed: " + e.getMessage());
        }
    }
}

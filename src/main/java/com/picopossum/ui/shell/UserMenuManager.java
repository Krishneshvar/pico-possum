package com.picopossum.ui.shell;

import com.picopossum.AppBootstrap;
import com.picopossum.ui.DependencyInjector;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.application.auth.AuthContext;

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

        VBox headerBox = new VBox(nameLabel);
        headerBox.setStyle("-fx-background-color: transparent;");

        CustomMenuItem headerItem = new CustomMenuItem(headerBox);
        headerItem.setHideOnClick(false);

        userMenuButton.getItems().addAll(
                headerItem, new SeparatorMenuItem()
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
}

package com.possum.ui.workspace;

import com.possum.infrastructure.logging.LoggingConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import com.possum.ui.common.accessibility.AccessibilityEnhancer;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.controls.ViewStateEnhancer;
import com.possum.ui.navigation.Parameterizable;

import java.util.Map;

public class WorkspaceManager {

    private final WorkspaceDesktop desktop;
    private com.possum.ui.DependencyInjector dependencyInjector;

    public WorkspaceManager(WorkspaceDesktop desktop, com.possum.ui.DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
        this.desktop = desktop;
    }

    /**
     * Opens a window for the given title/fxml.
     * If a window with that title is already open, brings it to the front instead
     * of creating a duplicate.
     */
    public void openOrFocusWindow(String title, String fxmlPath) {
        openOrFocusWindow(title, fxmlPath, null);
    }

    public void openOrFocusWindow(String title, String fxmlPath, Map<String, Object> params) {
        // Check for existing window with the same title
        for (InternalWindow w : desktop.getWindows()) {
            if (w.getTitle().equals(title)) {
                // If found, bring to front
                desktop.setActiveWindow(w);
                return;
            }
        }

        openWindow(title, fxmlPath, params);
    }

    public void openWindow(String title, String fxmlPath) {
        openWindow(title, fxmlPath, null);
    }

    public void openWindow(String title, String fxmlPath, Map<String, Object> params) {
        if (desktop.getWindows().size() >= 10) {
            NotificationService.warning("Only 10 active tabs are allowed. Please close some tabs.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (dependencyInjector != null) {
                loader.setControllerFactory(dependencyInjector.getControllerFactory());
            }
            Node content = loader.load();
            ViewStateEnhancer.enhance(content);
            AccessibilityEnhancer.enhance(content);

            Object controller = loader.getController();
            if (controller instanceof Parameterizable) {
                ((Parameterizable) controller).setParameters(params);
            }

            InternalWindow window = new InternalWindow(title);
            window.setContent(content);

            desktop.addWindow(window);

        } catch (Exception e) {
            e.printStackTrace();
            LoggingConfig.getLogger().error("Failed to load window '{}': {}", fxmlPath, e.getMessage(), e);
            NotificationService.error("Failed to open view. Please try again.");
        }
    }

    public void showDialog(String title, String fxmlPath, Map<String, Object> params) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            if (dependencyInjector != null) {
                loader.setControllerFactory(dependencyInjector.getControllerFactory());
            }
            javafx.scene.Parent root = loader.load();
            ViewStateEnhancer.enhance(root);
            AccessibilityEnhancer.enhance(root);

            Object controller = loader.getController();
            if (controller instanceof Parameterizable) {
                ((Parameterizable) controller).setParameters(params);
            }

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle(title);
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            if (desktop.getScene() != null && desktop.getScene().getWindow() != null) {
                stage.initOwner(desktop.getScene().getWindow());
            }

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            if (desktop.getScene() != null) {
                scene.getStylesheets().addAll(desktop.getScene().getStylesheets());
            }
            
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.showAndWait();

        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to show dialog '{}': {}", fxmlPath, e.getMessage(), e);
            NotificationService.error("Failed to open dialog. Please try again.");
        }
    }

    public void openDialog(String title, String fxmlPath) {
        showDialog(title, fxmlPath, null);
    }

    public void openDialog(String title, String fxmlPath, Map<String, Object> params) {
        showDialog(title, fxmlPath, params);
    }

    public void closeActiveWindow() {
        InternalWindow activeWindow = desktop.getActiveWindow();
        if (activeWindow != null) {
            desktop.removeWindow(activeWindow);
        }
    }

    /**
     * Closes the window or dialog containing the given node.
     */
    public void close(Node node) {
        if (node == null || node.getScene() == null) return;

        javafx.stage.Window window = node.getScene().getWindow();
        
        // If it's a separate Stage (Dialog), close it
        if (window instanceof javafx.stage.Stage stage && stage != desktop.getScene().getWindow()) {
            stage.close();
            return;
        }

        // Otherwise, look for an InternalWindow in the hierarchy
        Node current = node;
        while (current != null) {
            if (current instanceof InternalWindow internalWindow) {
                desktop.removeWindow(internalWindow);
                return;
            }
            current = current.getParent();
        }
    }

    public void setDependencyInjector(com.possum.ui.DependencyInjector dependencyInjector) {
        this.dependencyInjector = dependencyInjector;
    }

    public WorkspaceDesktop getDesktop() {
        return desktop;
    }
}

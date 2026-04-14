package com.possum.ui.common.controls;

import com.possum.ui.common.notifications.ToastPopup;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Legacy notification service, now upgraded to use the modern Toast system.
 */
public class NotificationService {
    private static StackPane notificationContainer;

    public enum Type {
        SUCCESS("toast-success", "bx-check-circle"),
        ERROR("toast-error", "bx-error-circle"),
        WARNING("toast-warning", "bx-error"),
        INFO("toast-info", "bx-info-circle");

        private final String styleClass;
        private final String iconCode;
        Type(String styleClass, String iconCode) { 
            this.styleClass = styleClass;
            this.iconCode = iconCode;
        }
    }

    public static void initialize(StackPane container) {
        notificationContainer = container;
    }

    public static void show(String message, Type type) {
        if (notificationContainer == null || notificationContainer.getScene() == null) {
            // Fallback to console if not initialized
            System.err.println("NotificationService: " + type.name() + ": " + message);
            return;
        }

        Window window = notificationContainer.getScene().getWindow();
        if (!(window instanceof Stage stage)) return;

        ToastPopup.show(stage, message, new ToastPopup.ToastSpec(type.styleClass, type.iconCode), Duration.seconds(5));
    }

    public static void success(String message) {
        show(message, Type.SUCCESS);
    }

    public static void error(String message) {
        show(message, Type.ERROR);
    }

    public static void warning(String message) {
        show(message, Type.WARNING);
    }

    public static void info(String message) {
        show(message, Type.INFO);
    }
}

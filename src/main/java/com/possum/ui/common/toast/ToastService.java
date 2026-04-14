package com.possum.ui.common.toast;

import com.possum.ui.common.notifications.ToastPopup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Service for displaying transient toast notifications.
 */
public class ToastService {

    private Stage mainStage;

    /**
     * Sets the main stage which will own the toast popups.
     * @param mainStage The application's primary stage.
     */
    public void setMainStage(Stage mainStage) {
        this.mainStage = mainStage;
    }

    /**
     * Shows a success toast notification.
     * @param message The message to display.
     */
    public void success(String message) {
        show(message, "toast-success", "bx-check-circle", Duration.seconds(4));
    }

    /**
     * Shows an error toast notification.
     * @param message The message to display.
     */
    public void error(String message) {
        show(message, "toast-error", "bx-error-circle", Duration.seconds(4));
    }

    /**
     * Shows an info/normal toast notification.
     * @param message The message to display.
     */
    public void info(String message) {
        show(message, "toast-info", "bx-info-circle", Duration.seconds(4));
    }

    /**
     * Shows a warning toast notification.
     * @param message The message to display.
     */
    public void warning(String message) {
        show(message, "toast-warning", "bx-error", Duration.seconds(4));
    }

    private void show(String message, String styleClass, String iconCode, Duration holdDuration) {
        if (mainStage == null) {
            System.err.println("ToastService: mainStage not set. Cannot show toast: " + message);
            return;
        }

        ToastPopup.show(mainStage, message, new ToastPopup.ToastSpec(styleClass, iconCode), holdDuration);
    }
}

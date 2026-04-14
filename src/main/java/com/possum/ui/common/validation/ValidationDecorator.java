package com.possum.ui.common.validation;

import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Handles visual feedback for validation errors on JavaFX controls.
 */
public final class ValidationDecorator {

    private static final String ERROR_STYLE_CLASS = "input-error";

    private ValidationDecorator() {}

    /**
     * Decorates a control based on a validation result.
     */
    public static void decorate(Control control, String errorMessage) {
        if (errorMessage != null && !errorMessage.isBlank()) {
            addErrorState(control, errorMessage);
        } else {
             removeErrorState(control);
        }
    }

    private static void addErrorState(Control control, String message) {
        if (!control.getStyleClass().contains(ERROR_STYLE_CLASS)) {
            control.getStyleClass().add(ERROR_STYLE_CLASS);
        }

        Tooltip tooltip = new Tooltip(message);
        tooltip.setShowDelay(Duration.millis(100));
        tooltip.getStyleClass().add("error-tooltip"); // Assume this or use inline
        tooltip.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 8px; -fx-font-size: 13px; -fx-font-weight: 600; -fx-background-radius: 4px;");
        control.setTooltip(tooltip);
    }

    private static void removeErrorState(Control control) {
        control.getStyleClass().remove(ERROR_STYLE_CLASS);
        control.setTooltip(null);
    }
}

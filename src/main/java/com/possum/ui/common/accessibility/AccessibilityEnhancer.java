package com.possum.ui.common.accessibility;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;

/**
 * Applies lightweight accessibility defaults to loaded views.
 */
public final class AccessibilityEnhancer {

    private AccessibilityEnhancer() {
    }

    public static void enhance(Node root) {
        if (root == null) {
            return;
        }
        visit(root);
    }

    private static void visit(Node node) {
        if (node instanceof Control control) {
            applyControlDefaults(control);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visit(child);
            }
        }
    }

    private static void applyControlDefaults(Control control) {
        if (!control.isFocusTraversable()) {
            control.setFocusTraversable(true);
        }

        String accessibleText = trimToNull(control.getAccessibleText());
        if (accessibleText != null) {
            return;
        }

        if (control instanceof Labeled labeled) {
            String labelText = trimToNull(labeled.getText());
            if (labelText != null) {
                control.setAccessibleText(labelText.replace("*", "").trim());
                return;
            }
        }

        if (control instanceof TextInputControl input) {
            String prompt = trimToNull(input.getPromptText());
            if (prompt != null) {
                control.setAccessibleText(prompt);
                return;
            }
        }

        if (control instanceof ComboBoxBase<?> comboBox) {
            String prompt = trimToNull(comboBox.getPromptText());
            if (prompt != null) {
                control.setAccessibleText(prompt);
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

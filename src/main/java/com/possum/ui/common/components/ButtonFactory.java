package com.possum.ui.common.components;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.Cursor;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Factory for creating styled buttons with icons.
 * Provides consistent button appearance across the application.
 */
public final class ButtonFactory {

    private ButtonFactory() {
        // Utility class
    }

    /**
     * Create a button with icon and text.
     */
    public static Button createButton(String text, String iconCode, Runnable action) {
        Button button = new Button(text);
        
        if (iconCode != null && !iconCode.isEmpty()) {
            FontIcon icon = new FontIcon(iconCode);
            icon.setIconSize(16);
            button.setGraphic(icon);
        }
        
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        
        button.setCursor(Cursor.HAND);
        return button;
    }

    /**
     * Create an icon-only button.
     */
    public static Button createIconButton(String iconCode, Runnable action) {
        return createButton("", iconCode, action);
    }

    /**
     * Create an icon button with tooltip.
     */
    public static Button createIconButton(String iconCode, String tooltipText, Runnable action) {
        Button button = createIconButton(iconCode, action);
        if (tooltipText != null && !tooltipText.isEmpty()) {
            button.setTooltip(new Tooltip(tooltipText));
        }
        return button;
    }

    /**
     * Create a primary action button.
     */
    public static Button createPrimaryButton(String text, String iconCode, Runnable action) {
        Button button = createButton(text, iconCode, action);
        button.getStyleClass().add("btn-primary");
        return button;
    }

    /**
     * Create a secondary action button.
     */
    public static Button createSecondaryButton(String text, String iconCode, Runnable action) {
        Button button = createButton(text, iconCode, action);
        button.getStyleClass().add("btn-secondary");
        return button;
    }

    /**
     * Create a destructive action button (delete, remove, etc.).
     */
    public static Button createDestructiveButton(String text, String iconCode, Runnable action) {
        Button button = createButton(text, iconCode, action);
        button.getStyleClass().add("btn-destructive");
        return button;
    }

    /**
     * Create a card action button (for product cards, etc.).
     */
    public static Button createCardActionButton(String iconCode, Runnable action) {
        Button button = createIconButton(iconCode, action);
        button.getStyleClass().add("card-action-btn");
        
        if (button.getGraphic() instanceof FontIcon icon) {
            icon.getStyleClass().add("card-action-icon");
        }
        
        return button;
    }

    /**
     * Create a destructive card action button.
     */
    public static Button createDestructiveCardActionButton(String iconCode, Runnable action) {
        Button button = createCardActionButton(iconCode, action);
        button.getStyleClass().add("card-action-btn-destructive");
        return button;
    }

    /**
     * Create a table action button.
     */
    public static Button createTableActionButton(String text, Runnable action) {
        Button button = createButton(text, null, action);
        button.getStyleClass().addAll("action-btn", "table-action-btn");
        return button;
    }

    /**
     * Create an edit action button.
     */
    public static Button createEditButton(Runnable action) {
        Button button = createIconButton("bx-edit", "Edit", action);
        button.getStyleClass().add("btn-edit-action");
        return button;
    }

    /**
     * Create an edit action button with text.
     */
    public static Button createEditButton(String text, Runnable action) {
        Button button = createButton(text, "bx-edit", action);
        button.getStyleClass().addAll("action-button", "btn-edit-action");
        button.setCursor(Cursor.HAND);
        
        if (button.getGraphic() instanceof FontIcon icon) {
            icon.setIconSize(14);
            icon.getStyleClass().add("table-action-icon");
        }
        
        return button;
    }

    /**
     * Create a delete action button.
     */
    public static Button createDeleteButton(Runnable action) {
        Button button = createIconButton("bx-trash", "Delete", action);
        button.getStyleClass().add("btn-delete-action");
        return button;
    }

    /**
     * Create a view/show action button.
     */
    public static Button createViewButton(Runnable action) {
        Button button = createIconButton("bx-show", "View Details", action);
        button.getStyleClass().add("btn-view-action");
        return button;
    }

    /**
     * Create an add/plus action button.
     */
    public static Button createAddButton(String text, Runnable action) {
        Button button = createButton(text, "bx-plus", action);
        button.getStyleClass().add("btn-add-action");
        
        if (button.getGraphic() instanceof FontIcon icon) {
            icon.setIconColor(javafx.scene.paint.Color.WHITE);
        }
        
        return button;
    }

    /**
     * Create a refresh button.
     */
    public static Button createRefreshButton(Runnable action) {
        Button button = createButton("Refresh", "bx-sync", action);
        button.getStyleClass().add("btn-refresh");
        return button;
    }

    /**
     * Create an import button.
     */
    public static Button createImportButton(Runnable action) {
        Button button = createButton("Import", "bx-import", action);
        button.getStyleClass().add("btn-import");
        return button;
    }

    /**
     * Create an export button.
     */
    public static Button createExportButton(Runnable action) {
        Button button = createButton("Export", "bx-export", action);
        button.getStyleClass().add("btn-export");
        return button;
    }

    /**
     * Apply add button styling to existing button.
     */
    public static void applyAddButtonStyle(Button button) {
        FontIcon icon = new FontIcon("bx-plus");
        icon.setIconSize(16);
        icon.setIconColor(javafx.scene.paint.Color.WHITE);
        button.setGraphic(icon);
    }

    /**
     * Apply refresh button styling to existing button.
     */
    public static void applyRefreshButtonStyle(Button button) {
        FontIcon icon = new FontIcon("bx-sync");
        icon.setIconSize(16);
        button.setGraphic(icon);
    }
}

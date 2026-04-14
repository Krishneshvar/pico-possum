package com.possum.ui.common.components;

import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating context menus and action menus.
 * Provides consistent menu appearance across the application.
 */
public class MenuBuilder {
    
    private final List<MenuItem> items = new ArrayList<>();

    /**
     * Add a menu item with text and action.
     */
    public MenuBuilder addItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        items.add(item);
        return this;
    }

    /**
     * Add a menu item with icon, text, and action.
     */
    public MenuBuilder addItem(String iconCode, String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        
        if (iconCode != null && !iconCode.isEmpty()) {
            FontIcon icon = new FontIcon(iconCode);
            icon.setIconSize(14);
            icon.getStyleClass().add("table-action-icon");
            item.setGraphic(icon);
        }
        
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        
        items.add(item);
        return this;
    }

    /**
     * Add a view action menu item.
     */
    public MenuBuilder addViewAction(String text, Runnable action) {
        return addItem("bx-show", text, action);
    }

    /**
     * Add an edit action menu item.
     */
    public MenuBuilder addEditAction(String text, Runnable action) {
        return addItem("bx-pencil", text, action);
    }

    /**
     * Add a delete action menu item (styled as destructive).
     */
    public MenuBuilder addDeleteAction(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.getStyleClass().add("logout-menu-item");
        
        FontIcon icon = new FontIcon("bx-trash");
        icon.setIconSize(14);
        icon.getStyleClass().add("table-action-icon-danger");
        item.setGraphic(icon);
        
        if (action != null) {
            item.setOnAction(e -> action.run());
        }
        
        items.add(item);
        return this;
    }

    /**
     * Add a separator.
     */
    public MenuBuilder addSeparator() {
        items.add(new SeparatorMenuItem());
        return this;
    }

    /**
     * Add a custom menu item.
     */
    public MenuBuilder addCustomItem(MenuItem item) {
        items.add(item);
        return this;
    }

    /**
     * Add multiple items at once.
     */
    public MenuBuilder addItems(MenuItem... menuItems) {
        for (MenuItem item : menuItems) {
            items.add(item);
        }
        return this;
    }

    /**
     * Build and return the list of menu items.
     */
    public List<MenuItem> build() {
        return new ArrayList<>(items);
    }

    /**
     * Get the number of items (excluding separators).
     */
    public int size() {
        return (int) items.stream()
            .filter(item -> !(item instanceof SeparatorMenuItem))
            .count();
    }

    /**
     * Check if builder is empty.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Clear all items.
     */
    public MenuBuilder clear() {
        items.clear();
        return this;
    }

    /**
     * Create a new MenuBuilder instance.
     */
    public static MenuBuilder create() {
        return new MenuBuilder();
    }
}

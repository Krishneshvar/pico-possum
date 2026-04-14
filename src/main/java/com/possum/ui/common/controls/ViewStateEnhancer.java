package com.possum.ui.common.controls;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

/**
 * Applies consistent empty-state and table styling to loaded view trees.
 */
public final class ViewStateEnhancer {

    private ViewStateEnhancer() {
    }

    public static void enhance(Node root) {
        if (root == null) {
            return;
        }
        visit(root);
    }

    private static void visit(Node node) {
        if (node instanceof TableView<?> tableView) {
            if (!tableView.getStyleClass().contains("data-table") && !tableView.getStyleClass().contains("pos-table")) {
                tableView.getStyleClass().add("data-table");
            }
            if (tableView.getPlaceholder() == null) {
                Label placeholder = new Label("No records available");
                placeholder.getStyleClass().add("table-empty-placeholder");
                tableView.setPlaceholder(placeholder);
            }
        }

        if (node instanceof ListView<?> listView) {
            if (listView.getPlaceholder() == null) {
                Label placeholder = new Label("No items to display");
                placeholder.getStyleClass().add("table-empty-placeholder");
                listView.setPlaceholder(placeholder);
            }
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visit(child);
            }
        }
    }
}

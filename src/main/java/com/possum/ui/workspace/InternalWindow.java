package com.possum.ui.workspace;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * InternalWindow is now a lightweight content holder.
 * It has no dragging, resizing, or maximize chrome —
 * it simply fills whatever space WorkspaceDesktop gives it.
 */
public class InternalWindow extends StackPane {

    private final String title;
    private Runnable onCloseRequest;

    public InternalWindow(String title) {
        this.title = title;
        getStyleClass().add("internal-window");
        setManaged(true);
    }

    public void setContent(Node content) {
        getChildren().clear();
        getChildren().add(content);
    }

    /** Called by the desktop when the tab's × is clicked. */
    public void setOnCloseRequest(Runnable handler) {
        this.onCloseRequest = handler;
    }

    public Runnable getOnCloseRequest() {
        return onCloseRequest;
    }

    public String getTitle() {
        return title;
    }

    /** No-op – kept so WorkspaceDesktop compile-calls work. */
    public void setActive(boolean active) {
        // Visual active state is handled by the tab bar, not the window itself.
    }
}

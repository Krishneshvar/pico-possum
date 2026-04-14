package com.possum.ui.shell;

import com.possum.ui.common.dialogs.DialogStyler;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Handles all global keyboard shortcut logic for the application shell.
 * Decoupled from AppShellController; requires only a StackPane for focus
 * lookups and a WorkspaceManager for window control.
 */
public class GlobalShortcutHandler {

    private final StackPane contentArea;
    private final WorkspaceManager workspaceManager;

    public GlobalShortcutHandler(StackPane contentArea, WorkspaceManager workspaceManager) {
        this.contentArea = contentArea;
        this.workspaceManager = workspaceManager;
    }

    public void install() {
        Platform.runLater(() -> {
            if (contentArea == null || contentArea.getScene() == null) return;
            contentArea.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handle);
        });
    }

    private void handle(KeyEvent event) {
        boolean commandOrCtrl = event.isControlDown() || event.isMetaDown();
        KeyCode code = event.getCode();

        if (commandOrCtrl && code == KeyCode.TAB) {
            if (workspaceManager != null) {
                workspaceManager.getDesktop().cycleActiveTab(event.isShiftDown() ? -1 : 1);
                event.consume();
            }
            return;
        }

        if (commandOrCtrl && code == KeyCode.W) {
            if (workspaceManager != null) {
                workspaceManager.getDesktop().closeActiveTab();
                event.consume();
            }
            return;
        }

        if (commandOrCtrl && code == KeyCode.K) {
            if (focusSearchField()) event.consume();
            return;
        }

        if (commandOrCtrl && code == KeyCode.S) {
            if (fireAction(List.of("save", "update", "apply", "complete"))) event.consume();
            return;
        }

        if (commandOrCtrl && code == KeyCode.N) {
            if (isPosActiveWindow()) return;
            if (fireAction(List.of("new", "create", "add"))) event.consume();
            return;
        }

        if (!commandOrCtrl && event.isShiftDown() && code == KeyCode.SLASH) {
            showShortcutHelp();
            event.consume();
            return;
        }

        if (code == KeyCode.ESCAPE) {
            if (tryCloseOwnedModal()) event.consume();
        }
    }

    private boolean focusSearchField() {
        Node root = getActiveContentRoot();
        TextInputControl field = findFirst(root, node ->
            node instanceof TextInputControl input &&
            containsAny(input.getPromptText(), List.of("search", "find", "filter")));
        if (field != null) { field.requestFocus(); field.selectAll(); return true; }
        return false;
    }

    private boolean fireAction(List<String> keywords) {
        Node root = getActiveContentRoot();
        Button target = findFirst(root, node -> {
            if (!(node instanceof Button btn)) return false;
            if (!btn.isVisible() || btn.isDisabled()) return false;
            String text = btn.getText() == null ? "" : btn.getText().toLowerCase(Locale.ROOT);
            if (btn.isDefaultButton() && !text.isBlank()) return true;
            return keywords.stream().anyMatch(text::contains);
        });
        if (target != null && !target.isDisabled() && target.isVisible()) {
            target.fire();
            return true;
        }
        return false;
    }

    private boolean tryCloseOwnedModal() {
        if (contentArea == null || contentArea.getScene() == null) return false;
        Scene scene = contentArea.getScene();
        if (scene.getWindow() instanceof javafx.stage.Stage stage) {
            for (javafx.stage.Window owned : javafx.stage.Window.getWindows()) {
                if (owned instanceof javafx.stage.Stage ownedStage
                        && ownedStage.isShowing()
                        && ownedStage.getOwner() == stage) {
                    ownedStage.close();
                    return true;
                }
            }
        }
        return false;
    }

    private void showShortcutHelp() {
        Alert help = new Alert(Alert.AlertType.INFORMATION);
        DialogStyler.apply(help);
        help.setTitle("Keyboard Shortcuts");
        help.setHeaderText("Keyboard Mastery");
        help.setContentText(
            "Ctrl+K: Focus Search\n" +
            "Ctrl+S: Save/Submit\n" +
            "Ctrl+N: Create New\n" +
            "Ctrl+Tab / Ctrl+Shift+Tab: Next/Previous Tab\n" +
            "Ctrl+W: Close Current Tab\n" +
            "Esc: Close Modal\n" +
            "?: Open Shortcuts Help"
        );
        help.show();
    }

    private Node getActiveContentRoot() {
        if (workspaceManager != null && workspaceManager.getDesktop() != null
                && workspaceManager.getDesktop().getActiveWindow() != null
                && !workspaceManager.getDesktop().getActiveWindow().getChildren().isEmpty()) {
            return workspaceManager.getDesktop().getActiveWindow().getChildren().get(0);
        }
        return contentArea;
    }

    private boolean isPosActiveWindow() {
        if (workspaceManager == null || workspaceManager.getDesktop() == null
                || workspaceManager.getDesktop().getActiveWindow() == null) return false;
        String title = workspaceManager.getDesktop().getActiveWindow().getTitle();
        return title != null && title.toLowerCase(Locale.ROOT).contains("point of sale");
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T findFirst(Node root, Predicate<Node> predicate) {
        if (root == null) return null;
        if (predicate.test(root)) return (T) root;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findFirst(child, predicate);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean containsAny(String source, List<String> terms) {
        if (source == null) return false;
        String lower = source.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(lower::contains);
    }
}

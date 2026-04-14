package com.possum.ui.common.dialogs;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

/**
 * Applies shared modal styling across all JavaFX dialogs and alerts.
 */
public final class DialogStyler {

    private DialogStyler() {
    }

    public static void apply(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }
        apply(dialog.getDialogPane());
    }

    public static void apply(DialogPane pane) {
        if (pane == null) {
            return;
        }

        String stylesheet = DialogStyler.class.getResource("/styles/app-shell.css").toExternalForm();
        if (!pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }
        if (!pane.getStyleClass().contains("modal-dialog")) {
            pane.getStyleClass().add("modal-dialog");
        }
    }
}

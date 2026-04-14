package com.possum.ui.common.notifications;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Shared toast popup renderer used by both toast services.
 */
public final class ToastPopup {

    private ToastPopup() {
    }

    public record ToastSpec(String styleClass, String iconCode) {
    }

    public static void show(Window owner, String message, ToastSpec spec, Duration holdDuration) {
        if (owner == null) {
            return;
        }

        Platform.runLater(() -> {
            Popup popup = new Popup();

            HBox toastBody = new HBox(12);
            toastBody.getStyleClass().addAll("toast-container", spec.styleClass());
            toastBody.setAlignment(Pos.CENTER_LEFT);
            toastBody.setPadding(new Insets(12, 20, 12, 20));
            toastBody.setMinWidth(320);
            toastBody.setMaxWidth(420);

            FontIcon icon = new FontIcon(spec.iconCode());
            icon.setIconSize(18);
            icon.getStyleClass().add("toast-icon");

            Label label = new Label(message);
            label.setWrapText(true);
            label.getStyleClass().add("toast-message");

            toastBody.getStylesheets().add(ToastPopup.class.getResource("/styles/app-shell.css").toExternalForm());
            toastBody.getChildren().addAll(icon, label);
            popup.getContent().add(toastBody);
            popup.setAutoHide(true);

            popup.setOnShown(e -> {
                double x = owner.getX() + (owner.getWidth() / 2) - (toastBody.getWidth() / 2);
                double y = owner.getY() + 76;
                popup.setX(x);
                popup.setY(y);
            });

            popup.show(owner);

            toastBody.setOpacity(0);
            Timeline fadeIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(toastBody.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(220), new KeyValue(toastBody.opacityProperty(), 1))
            );

            Timeline fadeOut = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(toastBody.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(220), new KeyValue(toastBody.opacityProperty(), 0))
            );
            fadeOut.setDelay(holdDuration);
            fadeOut.setOnFinished(ev -> popup.hide());

            fadeIn.play();
            fadeIn.setOnFinished(ev -> fadeOut.play());
        });
    }
}

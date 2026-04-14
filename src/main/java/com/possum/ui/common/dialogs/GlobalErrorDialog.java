package com.possum.ui.common.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A premium, glassmorphism-inspired error dialog for critical application failures.
 */
public class GlobalErrorDialog {

    public static void show(Throwable throwable) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Application Error");

        // Root container with glass effect
        VBox root = new VBox(20);
        root.setPadding(new Insets(30));
        root.setStyle(
            "-fx-background-color: rgba(255, 255, 255, 0.9);" +
            "-fx-background-radius: 15;" +
            "-fx-border-color: rgba(200, 200, 200, 0.5);" +
            "-fx-border-radius: 15;" +
            "-fx-border-width: 1;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 20, 0, 0, 10);"
        );
        root.setPrefWidth(500);

        // Header with Icon
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon errorIcon = new FontIcon("bx-error-circle");
        errorIcon.setIconSize(40);
        errorIcon.setIconColor(Color.web("#e11d48")); // Rose 600

        VBox titleBox = new VBox(2);
        Label titleLabel = new Label("Something went wrong");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#1e293b"));

        Label subtitleLabel = new Label("An unexpected error occurred in the application.");
        subtitleLabel.setTextFill(Color.web("#64748b"));
        
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        header.getChildren().addAll(errorIcon, titleBox);

        // Message
        Label messageLabel = new Label(throwable.getMessage() != null ? throwable.getMessage() : "Unknown error");
        messageLabel.setWrapText(true);
        messageLabel.setFont(Font.font("System", 14));
        messageLabel.setTextFill(Color.web("#334155"));
        messageLabel.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 15; -fx-background-radius: 10;");
        messageLabel.setMaxWidth(Double.MAX_VALUE);

        // Details Accordion
        Accordion accordion = new Accordion();
        TitledPane detailsPane = new TitledPane();
        detailsPane.setText("View Error Details");
        detailsPane.setAnimated(true);

        TextArea detailsArea = new TextArea(getStackTrace(throwable));
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setFont(Font.font("Monospaced", 12));
        detailsArea.setPrefHeight(200);
        detailsArea.setStyle("-fx-control-inner-background: #0f172a; -fx-text-fill: #94a3b8;");

        detailsPane.setContent(detailsArea);
        accordion.getPanes().add(detailsPane);

        // Actions
        HBox actions = new HBox(15);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button copyButton = new Button("Copy Error");
        copyButton.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 16;");
        copyButton.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(detailsArea.getText());
            clipboard.setContent(content);
            copyButton.setText("Copied!");
        });

        Button closeButton = new Button("Close Application");
        closeButton.setStyle("-fx-background-color: #1e293b; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 20; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> System.exit(1));

        Button ignoreButton = new Button("Try to Continue");
        ignoreButton.setStyle("-fx-background-color: #ffffff; -fx-border-color: #1976d2; -fx-text-fill: #1976d2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 16;");
        ignoreButton.setOnAction(e -> stage.close());

        actions.getChildren().addAll(copyButton, ignoreButton, closeButton);

        root.getChildren().addAll(header, messageLabel, accordion, actions);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}

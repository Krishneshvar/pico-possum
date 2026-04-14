package com.possum.ui.common.dialogs;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

public class StartupRepairDialog {

    public static void show(List<String> failures, Runnable onRetry, Runnable onExit, Consumer<String> onRepairAction) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(24);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12;");
        root.setPrefWidth(550);

        // Header
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon warningIcon = new FontIcon("bx-error-circle");
        warningIcon.setIconSize(32);
        warningIcon.setIconColor(javafx.scene.paint.Color.web("#ef4444"));
        
        VBox titleBox = new VBox(4);
        Label title = new Label("System Health Check Failed");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 700; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("The application cannot start safely due to the following issues:");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);
        
        header.getChildren().addAll(warningIcon, titleBox);

        // Failure List
        TextArea detailsArea = new TextArea(String.join("\n\n", failures));
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefHeight(150);
        detailsArea.setStyle("-fx-control-inner-background: #f8fafc; -fx-font-family: 'monospace'; -fx-font-size: 12px;");

        // Action Buttons
        VBox repairBox = new VBox(12);
        Label repairTitle = new Label("Available Repair Tools:");
        repairTitle.setStyle("-fx-font-weight: 600; -fx-text-fill: #1e293b;");
        
        HBox repairActions = new HBox(12);
        Button reindexBtn = createRepairButton("Re-index DB", "bx-refresh", "#3b82f6");
        reindexBtn.setOnAction(e -> onRepairAction.accept("REINDEX"));
        
        Button vacuumBtn = createRepairButton("Compact DB", "bx-unite", "#3b82f6");
        vacuumBtn.setOnAction(e -> onRepairAction.accept("VACUUM"));
        
        Button backupBtn = createRepairButton("Open Backups", "bx-folder-open", "#64748b");
        backupBtn.setOnAction(e -> onRepairAction.accept("OPEN_BACKUPS"));
        
        repairActions.getChildren().addAll(reindexBtn, vacuumBtn, backupBtn);
        repairBox.getChildren().addAll(repairTitle, repairActions);

        // Footer Actions
        HBox footer = new HBox(16);
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        Button exitBtn = new Button("Exit App");
        exitBtn.getStyleClass().add("btn-secondary");
        exitBtn.setOnAction(e -> onExit.run());
        
        Button retryBtn = new Button("Retry Startup");
        retryBtn.getStyleClass().add("btn-primary");
        retryBtn.setStyle("-fx-background-color: #10b981;");
        retryBtn.setOnAction(e -> {
            stage.close();
            onRetry.run();
        });
        
        footer.getChildren().addAll(exitBtn, retryBtn);

        root.getChildren().addAll(header, detailsArea, repairBox, footer);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(StartupRepairDialog.class.getResource("/styles/main.css").toExternalForm());
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.showAndWait();
    }

    private static Button createRepairButton(String text, String iconCode, String color) {
        Button btn = new Button(text);
        FontIcon icon = new FontIcon(iconCode);
        icon.setIconSize(14);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        btn.setGraphic(icon);
        btn.setStyle("-fx-background-color: transparent; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-text-fill: #334155; -fx-font-size: 13px;");
        HBox.setHgrow(btn, Priority.ALWAYS);
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }
}

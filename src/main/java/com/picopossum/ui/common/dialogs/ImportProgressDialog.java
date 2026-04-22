package com.picopossum.ui.common.dialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javafx.scene.layout.HBox;

public final class ImportProgressDialog {

    private final Stage stage;
    private final Label totalLabel;
    private final Label successLabel;
    private final Label processedLabel;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Button closeButton;

    private int totalRecords;
    public ImportProgressDialog(Window owner, String title) {
        this.totalRecords = 0;

        stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setResizable(false);

        // Header with Icon
        org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon("bx-cloud-upload");
        icon.setIconSize(24);
        icon.setIconColor(javafx.scene.paint.Color.valueOf("#10B981"));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setStyle("-fx-font-size: 18px;");

        HBox header = new HBox(12, icon, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 16, 0));
        header.setStyle("-fx-border-color: -color-border; -fx-border-width: 0 0 1 0;");

        // Labels
        totalLabel = new Label("Total records found: 0");
        totalLabel.getStyleClass().add("form-label");
        
        successLabel = new Label("Successfully imported: 0");
        successLabel.getStyleClass().add("form-label");
        successLabel.setStyle("-fx-text-fill: -color-success-text;");
        
        processedLabel = new Label("Processed: 0 / 0");
        processedLabel.getStyleClass().add("form-label");
        processedLabel.setStyle("-fx-font-weight: 700;");

        // Progress
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setMinHeight(12);
        progressBar.getStyleClass().add("import-progress-bar");

        statusLabel = new Label("Preparing import pipeline...");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-text-muted; -fx-italic: true;");
        statusLabel.setWrapText(true);
        statusLabel.setPrefWidth(400);

        closeButton = new Button("Close Dialog");
        closeButton.getStyleClass().add("primary-button");
        closeButton.setPrefWidth(140);
        closeButton.setOnAction(event -> stage.close());
        closeButton.setDisable(true); // Disable until complete or fail

        VBox content = new VBox(15, totalLabel, successLabel, processedLabel, progressBar, statusLabel);
        content.setPadding(new Insets(20, 0, 20, 0));

        HBox footer = new HBox(closeButton);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(header, content, footer);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("card");
        root.setPrefWidth(480);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.initStyle(javafx.stage.StageStyle.DECORATED);

        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            scene.getStylesheets().addAll(ownerStage.getScene().getStylesheets());
        }
        
        // Add custom progress bar style
        scene.getStylesheets().add(getClass().getResource("/styles/components.css").toExternalForm());
        
        stage.setScene(scene);
    }

    public void show() {
        runOnFxThread(() -> {
            stage.show();
            stage.centerOnScreen();
        });
    }

    public void setTotalRecords(int total) {
        runOnFxThread(() -> {
            totalRecords = Math.max(0, total);
            totalLabel.setText("Total records found: " + totalRecords);
            processedLabel.setText("Processed: 0 / " + totalRecords);
            progressBar.setProgress(totalRecords == 0 ? 1 : 0);
            if (totalRecords == 0) {
                statusLabel.setText("No data rows found to import.");
                closeButton.setDisable(false);
            } else {
                statusLabel.setText("Import in progress...");
            }
        });
    }

    public void updateProgress(int processed, int successful) {
        runOnFxThread(() -> {
            int safeProcessed = Math.max(0, processed);
            int safeSuccessful = Math.max(0, successful);
            int safeTotal = Math.max(0, totalRecords);

            successLabel.setText("Successfully imported: " + safeSuccessful);
            processedLabel.setText("Processed: " + safeProcessed + " / " + safeTotal);

            if (safeTotal == 0) {
                progressBar.setProgress(1);
            } else {
                progressBar.setProgress(Math.min(1.0, (double) safeProcessed / safeTotal));
            }
        });
    }

    public void complete(int processed, int successful, int skipped) {
        runOnFxThread(() -> {
            updateProgress(processed, successful);
            statusLabel.setText("Import completed. " + Math.max(0, skipped) + " row(s) skipped.");
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-success-text; -fx-font-weight: 700;");
            
            org.kordamp.ikonli.javafx.FontIcon successIcon = new org.kordamp.ikonli.javafx.FontIcon("bx-check-circle");
            successIcon.setIconSize(18);
            successIcon.setIconColor(javafx.scene.paint.Color.valueOf("#10B981"));
            statusLabel.setGraphic(successIcon);
            
            closeButton.setDisable(false);
        });
    }

    public void fail(String message) {
        runOnFxThread(() -> {
            statusLabel.setText("Import failed: " + (message == null ? "Unknown error" : message));
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-error-text; -fx-font-weight: 700;");
            
            org.kordamp.ikonli.javafx.FontIcon failIcon = new org.kordamp.ikonli.javafx.FontIcon("bx-error-circle");
            failIcon.setIconSize(18);
            failIcon.setIconColor(javafx.scene.paint.Color.valueOf("#EF4444"));
            statusLabel.setGraphic(failIcon);
            
            closeButton.setDisable(false);
        });
    }

    private void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}

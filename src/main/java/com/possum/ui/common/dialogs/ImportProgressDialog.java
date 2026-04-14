package com.possum.ui.common.dialogs;

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

public final class ImportProgressDialog {

    private final Stage stage;
    private final Label totalLabel;
    private final Label successLabel;
    private final Label processedLabel;
    private final Label statusLabel;
    private final ProgressBar progressBar;

    private int totalRecords;

    public ImportProgressDialog(Window owner, String title) {
        this.totalRecords = 0;

        stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        totalLabel = new Label("Total records: 0");
        successLabel = new Label("Uploaded successfully: 0");
        processedLabel = new Label("Processed: 0 / 0");
        statusLabel = new Label("Preparing import...");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> stage.close());

        VBox root = new VBox(10, totalLabel, successLabel, processedLabel, progressBar, statusLabel, closeButton);
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPrefWidth(460);

        Scene scene = new Scene(root);
        if (owner instanceof Stage ownerStage && ownerStage.getScene() != null) {
            scene.getStylesheets().addAll(ownerStage.getScene().getStylesheets());
        }
        stage.setScene(scene);
    }

    public void show() {
        runOnFxThread(stage::show);
    }

    public void setTotalRecords(int total) {
        runOnFxThread(() -> {
            totalRecords = Math.max(0, total);
            totalLabel.setText("Total records: " + totalRecords);
            processedLabel.setText("Processed: 0 / " + totalRecords);
            progressBar.setProgress(totalRecords == 0 ? 1 : 0);
            if (totalRecords == 0) {
                statusLabel.setText("No data rows found to import.");
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

            successLabel.setText("Uploaded successfully: " + safeSuccessful);
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
            statusLabel.setText("Import completed. Skipped/failed: " + Math.max(0, skipped));
        });
    }

    public void fail(String message) {
        runOnFxThread(() -> statusLabel.setText("Import failed: " + (message == null ? "Unknown error" : message)));
    }

    private void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}

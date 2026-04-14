package com.possum.ui.common.controllers;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.ImportProgressDialog;
import com.possum.shared.util.CsvImportUtil;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract controller for CSV import functionality.
 * Eliminates duplicate import code across Products, Customers, Categories, etc.
 * 
 * @param <T> Entity type being imported
 * @param <R> Import record type (intermediate representation)
 */
public abstract class AbstractImportController<T, R> {

    @FXML public Button importButton;
    
    /**
     * Get required CSV headers for detection
     */
    protected abstract String[] getRequiredHeaders();

    /**
     * Parse a CSV row into an import record
     */
    protected abstract R parseRow(List<String> row, Map<String, Integer> headers);

    /**
     * Create entity from import record
     */
    protected abstract T createEntity(R record) throws Exception;

    /**
     * Get import dialog title
     */
    protected abstract String getImportTitle();

    /**
     * Get entity name for messages
     */
    protected abstract String getEntityName();

    /**
     * Callback after successful import
     */
    protected abstract void onImportComplete();

    /**
     * Handle import button click
     */
    @FXML
    public void handleImport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(getImportTitle());
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showOpenDialog(
            importButton != null ? importButton.getScene().getWindow() : null
        );
        
        if (file == null) return;

        AuthUser currentUser = AuthContext.getCurrentUser();
        if (currentUser == null) {
            NotificationService.error("No active user session found. Please sign in again and retry import.");
            return;
        }

        javafx.stage.Window owner = importButton != null && importButton.getScene() != null
            ? importButton.getScene().getWindow()
            : null;
        
        ImportProgressDialog progressDialog = new ImportProgressDialog(owner, getImportTitle());
        progressDialog.show();

        Task<ImportResult> importTask = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                AuthContext.setCurrentUser(currentUser);
                try {
                    List<List<String>> rows = CsvImportUtil.readCsv(file.toPath());
                    int headerIndex = findHeaderIndex(rows);
                    
                    if (headerIndex < 0) {
                        throw new IllegalArgumentException("Could not find a valid header row in CSV.");
                    }

                    Map<String, Integer> headers = CsvImportUtil.buildHeaderIndex(rows.get(headerIndex));
                    List<R> records = new ArrayList<>();

                    for (int i = headerIndex + 1; i < rows.size(); i++) {
                        List<String> row = rows.get(i);
                        if (CsvImportUtil.isRowEmpty(row)) {
                            continue;
                        }

                        R record = parseRow(row, headers);
                        if (record != null) {
                            records.add(record);
                        }
                    }

                    int totalRecords = records.size();
                    progressDialog.setTotalRecords(totalRecords);

                    int processed = 0;
                    int imported = 0;
                    int skipped = 0;

                    for (R record : records) {
                        processed++;
                        try {
                            createEntity(record);
                            imported++;
                        } catch (Exception ex) {
                            skipped++;
                        }
                        progressDialog.updateProgress(processed, imported);
                    }

                    return new ImportResult(totalRecords, imported, skipped);
                } finally {
                    AuthContext.clear();
                }
            }
        };

        importTask.setOnSucceeded(event -> {
            ImportResult result = importTask.getValue();
            progressDialog.complete(result.totalRecords(), result.imported(), result.skipped());
            onImportComplete();

            if (result.skipped() == 0) {
                NotificationService.success("Imported " + result.imported() + " " + getEntityName() + " successfully.");
            } else {
                NotificationService.warning("Imported " + result.imported() + " " + getEntityName() + ". " + result.skipped() + " row(s) skipped.");
            }
        });

        importTask.setOnFailed(event -> {
            Throwable error = importTask.getException();
            String message = error != null && error.getMessage() != null ? error.getMessage() : "Unknown error";
            progressDialog.fail(message);
            NotificationService.error("Failed to import " + getEntityName() + ": " + message);
        });

        Thread worker = new Thread(importTask, getEntityName() + "-import-task");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Find header row index in CSV
     */
    private int findHeaderIndex(List<List<String>> rows) {
        String[] required = getRequiredHeaders();
        for (String header : required) {
            int index = CsvImportUtil.findHeaderRowIndex(rows, header);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Import result record
     */
    protected record ImportResult(int totalRecords, int imported, int skipped) {}
}

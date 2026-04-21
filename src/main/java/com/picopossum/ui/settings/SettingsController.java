package com.picopossum.ui.settings;

import com.picopossum.infrastructure.backup.DatabaseBackupService;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.filesystem.UploadStore;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.infrastructure.printing.PrintOutcome;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.infrastructure.system.SystemInteropService;
import com.picopossum.shared.dto.GeneralSettings;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.ui.common.dialogs.DialogStyler;
import com.picopossum.shared.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.concurrent.Task;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Optional;

public class SettingsController {
    
    private static final DateTimeFormatter BACKUP_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault());

    @FXML private TextField currencyField;
    @FXML private ComboBox<String> dateFormatCombo;
    @FXML private ComboBox<String> timeFormatCombo;
    @FXML private CheckBox inventoryAlertsToggle;
    @FXML private ComboBox<String> printerCombo;
    @FXML private AnchorPane billSettingsTabContent;
    @FXML private Button testPrintBtn;
    @FXML private Button createBackupBtn;
    @FXML private Button restoreBackupBtn;
    @FXML private Button openBackupFolderBtn;
    @FXML private Label backupLocationLabel;
    @FXML private Label latestBackupLabel;
    
    private SettingsStore settingsStore;
    private PrinterService printerService;
    private DatabaseBackupService backupService;
    private SystemInteropService systemInteropService;
    private UploadStore uploadStore;
    private GeneralSettings generalSettings;
    private boolean syncingPrinterSelection = false;

    public SettingsController(SettingsStore settingsStore, PrinterService printerService, 
                              DatabaseBackupService backupService, SystemInteropService systemInteropService,
                              UploadStore uploadStore) {
        this.settingsStore = settingsStore;
        this.printerService = printerService;
        this.backupService = backupService;
        this.systemInteropService = systemInteropService;
        this.uploadStore = uploadStore;
    }

    @FXML
    public void initialize() {
        setupDateAndTimeFormats();
        loadGeneralSettings();
        configurePrinterSelectionPersistence();
        loadPrinters();
        setupBillSettings();
        setupBackupSettings();
    }



    private void setupDateAndTimeFormats() {
        dateFormatCombo.setItems(FXCollections.observableArrayList(
            "DD/MM/YYYY",
            "MM/DD/YYYY",
            "YYYY/MM/DD",
            "Month Date, Year",
            "Date Month, Year"
        ));

        timeFormatCombo.setItems(FXCollections.observableArrayList(
            "12 hour format",
            "24 hour format"
        ));
    }

    private void loadGeneralSettings() {
        generalSettings = settingsStore.loadGeneralSettings();
        currencyField.setText(generalSettings.getCurrencySymbol() != null ? generalSettings.getCurrencySymbol() : "");

        String dateFormat = generalSettings.getDateFormat();
        if (dateFormatCombo.getItems().contains(dateFormat)) {
            dateFormatCombo.setValue(dateFormat);
        } else {
            dateFormatCombo.setValue("DD/MM/YYYY");
        }

        String timeFormat = generalSettings.getTimeFormat();
        if (timeFormatCombo.getItems().contains(timeFormat)) {
            timeFormatCombo.setValue(timeFormat);
        } else {
            timeFormatCombo.setValue("12 hour format");
        }

        if (inventoryAlertsToggle != null) {
            inventoryAlertsToggle.setSelected(generalSettings.isInventoryAlertsAndRestrictionsEnabled());
        }


    }

    private void loadPrinters() {
        testPrintBtn.setDisable(true);
        printerCombo.setPromptText("Scanning printers...");
        
        Task<List<String>> scanTask = new Task<>() {
            @Override
            protected List<String> call() {
                return printerService.listPrinters();
            }
        };

        scanTask.setOnSucceeded(event -> {
            List<String> printers = scanTask.getValue();
            printerCombo.setItems(FXCollections.observableArrayList(printers));
            syncingPrinterSelection = true;
            try {
                if (printers.isEmpty()) {
                    printerCombo.setValue(null);
                    printerCombo.setPromptText("No printers found");
                    return;
                }

                String savedPrinter = generalSettings != null ? generalSettings.getDefaultPrinterName() : null;
                if (savedPrinter != null && !savedPrinter.isBlank()) {
                    if (printers.contains(savedPrinter)) {
                        printerCombo.setValue(savedPrinter);
                        printerCombo.setPromptText("Select a printer");
                        testPrintBtn.setDisable(false);
                    } else {
                        printerCombo.setValue(null);
                        printerCombo.setPromptText("Saved printer unavailable - reselect");
                    }
                    return;
                }

                String defaultPrinter = printerService.getDefaultPrinterName();
                if (defaultPrinter != null && printers.contains(defaultPrinter)) {
                    printerCombo.setValue(defaultPrinter);
                } else {
                    printerCombo.setValue(printers.get(0));
                }
                printerCombo.setPromptText("Select a printer");
                testPrintBtn.setDisable(false);
            } finally {
                syncingPrinterSelection = false;
            }
        });

        scanTask.setOnFailed(event -> {
            printerCombo.setPromptText("Failed to load printers");
            LoggingConfig.getLogger().error("Printer scan failed", scanTask.getException());
        });

        Thread thread = new Thread(scanTask, "printer-scan-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void configurePrinterSelectionPersistence() {
        printerCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (syncingPrinterSelection || newValue == null || newValue.isBlank() || newValue.equals(oldValue)) {
                return;
            }

            try {
                GeneralSettings settings = settingsStore.loadGeneralSettings();
                settings.setDefaultPrinterName(newValue);
                settingsStore.saveGeneralSettings(settings);
                if (generalSettings != null) {
                    generalSettings.setDefaultPrinterName(newValue);
                }
                testPrintBtn.setDisable(false);
                NotificationService.success("Default printer saved: " + newValue);
            } catch (Exception ex) {
                NotificationService.error("Failed to save default printer: " + ex.getMessage());
            }
        });
    }

    private String resolvePaperWidthForPrinting() {
        try {
            return settingsStore.loadBillSettings().getPaperWidth();
        } catch (Exception ex) {
            return "80mm";
        }
    }

    @FXML
    private void handleSaveGeneral() {
        try {
            GeneralSettings settings = settingsStore.loadGeneralSettings();
            settings.setCurrencySymbol(currencyField.getText());
            settings.setDateFormat(dateFormatCombo.getValue());
            settings.setTimeFormat(timeFormatCombo.getValue());
            settings.setInventoryAlertsAndRestrictionsEnabled(
                    inventoryAlertsToggle == null || inventoryAlertsToggle.isSelected()
            );
            
            settingsStore.saveGeneralSettings(settings);
            generalSettings = settings;
            CurrencyUtil.refreshCache();
            NotificationService.success("General settings updated");
        } catch (Exception e) {
            NotificationService.error("Failed to save settings: " + e.getMessage());
        }
    }

    @FXML
    private void handleTestPrint() {
        String printer = printerCombo.getValue();
        if (printer == null || printer.isBlank()) {
            NotificationService.warning("Select a printer before testing.");
            return;
        }
        
        String testHtml = "<html><body><h2>Test Print</h2><p>This is a test receipt from POSSUM POS</p></body></html>";
        String paperWidth = resolvePaperWidthForPrinting();
        testPrintBtn.setDisable(true);
        testPrintBtn.setText("Printing...");

        printerService.printInvoiceDetailed(testHtml, printer, paperWidth)
            .thenAccept(outcome -> Platform.runLater(() -> onTestPrintFinished(outcome, printer)))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    testPrintBtn.setDisable(false);
                    testPrintBtn.setText("Test Print");
                    NotificationService.error("Print error: " + ex.getMessage());
                });
                return null;
            });
    }

    private void onTestPrintFinished(PrintOutcome outcome, String fallbackPrinterName) {
        testPrintBtn.setDisable(false);
        testPrintBtn.setText("Test Print");
        if (outcome.success()) {
            String target = outcome.printerName() != null ? outcome.printerName() : fallbackPrinterName;
            NotificationService.success("Test print sent successfully to " + target);
        } else {
            NotificationService.error("Test print failed: " + outcome.message());
        }
    }

    @FXML
    private void handleRefreshPrinters() {
        loadPrinters();
        NotificationService.success("Printers refreshed");
    }


    private void setupBillSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/settings/bill-settings-view.fxml"));
            BillSettingsController controller = new BillSettingsController(settingsStore, uploadStore);
            loader.setController(controller);
            Parent billSettingsView = loader.load();
            
            billSettingsTabContent.getChildren().setAll(billSettingsView);
            AnchorPane.setTopAnchor(billSettingsView, 0.0);
            AnchorPane.setBottomAnchor(billSettingsView, 0.0);
            AnchorPane.setLeftAnchor(billSettingsView, 0.0);
            AnchorPane.setRightAnchor(billSettingsView, 0.0);
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to load embedded bill settings: {}", e.getMessage(), e);
            NotificationService.error("Failed to load embedded bill settings: " + com.picopossum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    private void setupBackupSettings() {
        if (backupService == null) {
            return;
        }
        if (backupLocationLabel != null) {
            backupLocationLabel.setText(backupService.getBackupsDirectory().toString());
        }
        refreshBackupStatus();
    }

    @FXML
    private void handleCreateBackupNow() {
        if (backupService == null) {
            NotificationService.error("Backup service is unavailable");
            return;
        }

        Task<Path> backupTask = new Task<>() {
            @Override
            protected Path call() {
                return backupService.createManualBackup();
            }
        };

        backupTask.setOnRunning(event -> setBackupActionsDisabled(true));
        backupTask.setOnSucceeded(event -> {
            setBackupActionsDisabled(false);
            Path backupPath = backupTask.getValue();
            refreshBackupStatus();
            NotificationService.success("Backup created: " + backupPath.getFileName());
        });
        backupTask.setOnFailed(event -> {
            setBackupActionsDisabled(false);
            Throwable ex = backupTask.getException();
            NotificationService.error("Failed to create backup: " + buildErrorMessage(ex));
        });

        Thread thread = new Thread(backupTask, "manual-backup-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleOpenBackupFolder() {
        if (backupService == null) {
            NotificationService.error("Backup service is unavailable");
            return;
        }

        Path backupDir = backupService.getBackupsDirectory();
        
        Task<Boolean> openTask = new Task<>() {
            @Override
            protected Boolean call() {
                return systemInteropService.openFolder(backupDir);
            }
        };

        openTask.setOnRunning(event -> setBackupActionsDisabled(true));
        openTask.setOnSucceeded(event -> {
            setBackupActionsDisabled(false);
            if (!openTask.getValue()) {
                NotificationService.info("Backup folder location: " + backupDir);
            }
        });
        openTask.setOnFailed(event -> {
            setBackupActionsDisabled(false);
            NotificationService.error("Failed to open backup folder");
        });

        Thread thread = new Thread(openTask, "open-folder-task");
        thread.setDaemon(true);
        thread.start();
    }


    @FXML
    private void handleRestoreFromBackup() {
        if (backupService == null) {
            NotificationService.error("Backup service is unavailable");
            return;
        }

        Window owner = resolveWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite Backup Files", "*.db"));
        chooser.setInitialDirectory(backupService.getBackupsDirectory().toFile());

        java.io.File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(confirm);
        confirm.setTitle("Restore Database");
        confirm.setHeaderText("Restore data from selected backup?");
        confirm.setContentText(
                "This will overwrite your current database.\n\n" +
                "A pre-restore safety backup will be created automatically.\n\n" +
                "Continue with restore?"
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        Path backupPath = selected.toPath();
        Task<DatabaseBackupService.RestoreResult> restoreTask = new Task<>() {
            @Override
            protected DatabaseBackupService.RestoreResult call() {
                return backupService.restoreFromBackup(backupPath);
            }
        };

        restoreTask.setOnRunning(event -> setBackupActionsDisabled(true));
        restoreTask.setOnSucceeded(event -> {
            setBackupActionsDisabled(false);
            DatabaseBackupService.RestoreResult result = restoreTask.getValue();
            refreshBackupStatus();

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            DialogStyler.apply(success);
            success.setTitle("Restore Completed");
            success.setHeaderText("Backup restored successfully");
            success.setContentText(
                    "Restored from: " + result.sourceBackup().getFileName() + "\n" +
                    "Safety backup created: " + result.preRestoreBackup().getFileName() + "\n\n" +
                    "Please restart the app to refresh all open screens."
            );
            success.showAndWait();
        });
        restoreTask.setOnFailed(event -> {
            setBackupActionsDisabled(false);
            Throwable ex = restoreTask.getException();
            NotificationService.error("Failed to restore backup: " + buildErrorMessage(ex));
        });

        Thread thread = new Thread(restoreTask, "restore-backup-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshBackupStatus() {
        if (backupService == null || latestBackupLabel == null) {
            return;
        }

        Optional<Path> latest = backupService.findLatestBackup();
        if (latest.isEmpty()) {
            latestBackupLabel.setText("No backups created yet");
            return;
        }

        Path latestFile = latest.get();
        String modifiedAt = "Unknown time";
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(latestFile);
            modifiedAt = BACKUP_TIME_FORMAT.format(Instant.ofEpochMilli(lastModifiedTime.toMillis()));
        } catch (Exception ignored) {
            // Use fallback string if file attributes cannot be read.
        }
        latestBackupLabel.setText(latestFile.getFileName() + " (" + modifiedAt + ")");
    }

    private void setBackupActionsDisabled(boolean disabled) {
        Platform.runLater(() -> {
            if (createBackupBtn != null) {
                createBackupBtn.setDisable(disabled);
            }
            if (restoreBackupBtn != null) {
                restoreBackupBtn.setDisable(disabled);
            }
            if (openBackupFolderBtn != null) {
                openBackupFolderBtn.setDisable(disabled);
            }
        });
    }

    private Window resolveWindow() {
        if (currencyField != null && currencyField.getScene() != null) {
            return currencyField.getScene().getWindow();
        }
        return null;
    }

    private static String buildErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            return throwable.getMessage();
        }
        return throwable.getClass().getSimpleName();
    }
}

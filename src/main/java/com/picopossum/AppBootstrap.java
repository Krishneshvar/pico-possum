package com.picopossum;

import com.picopossum.application.ApplicationModule;
import com.picopossum.application.sales.SalesService;
import com.picopossum.ui.sales.ProductSearchIndex;

import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.ui.DependencyInjector;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.backup.DatabaseBackupService;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.infrastructure.lazy.ServiceLocator;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.ui.AppShellController;

public final class AppBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppBootstrap.class);

    private AppPaths appPaths;
    private DatabaseManager databaseManager;
    private ServiceLocator serviceLocator;
    private DependencyInjector dependencyInjector;



    public void start(Stage stage) {
        try {
            initializeCore();
            runStartupHealthChecks(stage);

            showLogin(stage);
        } catch (RuntimeException ex) {
            shutdown();
            throw ex;
        }
    }

    public void shutdown() {
        if (serviceLocator != null) {
            serviceLocator.getDatabaseBackupService().stopDailyBackups();
            serviceLocator.getAuditService().shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        LOGGER.info("Application shutdown completed");
    }

    public ServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    private void initializeCore() {
        appPaths = new AppPaths();
        LoggingConfig.configure(appPaths);
        setupGlobalExceptionHandler();
        initializePersistence();
        initializeApplication();
        initializeUI();
        LOGGER.info("Core services initialized");
    }

    private void setupGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            LOGGER.error("Uncaught exception in thread {}: {}", thread.getName(), throwable.getMessage(), throwable);
            if (Platform.isFxApplicationThread()) {
                com.picopossum.ui.common.dialogs.GlobalErrorDialog.show(throwable);
            } else {
                Platform.runLater(() -> com.picopossum.ui.common.dialogs.GlobalErrorDialog.show(throwable));
            }
        });
    }

    private void initializePersistence() {
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();
        TransactionManager tm = new TransactionManager(databaseManager);
        serviceLocator = new ServiceLocator(databaseManager, tm, appPaths);
        serviceLocator.getDatabaseBackupService().startDailyBackups();
    }

    private void initializeApplication() {
        // Services will be initialized lazily via ServiceLocator
    }

    private void initializeUI() {
        dependencyInjector = new DependencyInjector(serviceLocator);
        dependencyInjector.getToastService().setMainStage(null);
    }

    private void runStartupHealthChecks(Stage stage) {
        java.util.List<String> failures = new java.util.ArrayList<>();

        // 1. Data directory writable
        try {
            java.nio.file.Files.createDirectories(appPaths.getAppRoot());
            if (!java.nio.file.Files.isWritable(appPaths.getAppRoot())) {
                failures.add("Application data directory is not writable: " + appPaths.getAppRoot());
            }
        } catch (Exception e) {
            failures.add("Failed to initialize data directories: " + e.getMessage());
        }

        // 2. Database integrity check
        try (java.sql.Connection conn = databaseManager.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            if (rs.next()) {
                String result = rs.getString(1);
                if (!"ok".equalsIgnoreCase(result)) {
                    failures.add("Database integrity check failed: " + result + ". Try repairing or restoring from a backup.");
                }
            }
        } catch (Exception e) {
            failures.add("Could not perform database integrity check: " + e.getMessage());
        }

        // 3. Backup directory writable
        try {
            java.nio.file.Path backupDir = appPaths.getBackupsDir();
            java.nio.file.Files.createDirectories(backupDir);
            if (!java.nio.file.Files.isWritable(backupDir)) {
                failures.add("Backup directory is not writable: " + backupDir);
            }
        } catch (Exception e) {
            failures.add("Backup directory check failed: " + e.getMessage());
        }

        if (!failures.isEmpty()) {
            LOGGER.error("Startup health check failed:\n{}", String.join("\n", failures));
            com.picopossum.ui.common.dialogs.StartupRepairDialog.show(
                failures,
                () -> this.start(stage), // Retry
                () -> { shutdown(); javafx.application.Platform.exit(); System.exit(1); }, // Exit
                action -> handleRepairAction(action, stage) // Repair
            );
        }
    }

    private void handleRepairAction(String action, Stage stage) {
        try (java.sql.Connection conn = databaseManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            switch (action) {
                case "REINDEX":
                    stmt.execute("REINDEX");
                    Platform.runLater(() -> com.picopossum.ui.common.controls.NotificationService.success("Database indices rebuilt successfully."));
                    break;
                case "VACUUM":
                    stmt.execute("VACUUM");
                    Platform.runLater(() -> com.picopossum.ui.common.controls.NotificationService.success("Database compacted and optimized."));
                    break;
                case "OPEN_BACKUPS":
                    java.awt.Desktop.getDesktop().open(appPaths.getBackupsDir().toFile());
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("Repair action {} failed", action, e);
            Platform.runLater(() -> com.picopossum.ui.common.dialogs.GlobalErrorDialog.show(new RuntimeException("Repair failed: " + e.getMessage())));
        }
    }

    private void loadMainShell(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(AppBootstrap.class.getResource("/fxml/app-shell.fxml"));
            loader.setControllerFactory(dependencyInjector.getControllerFactory());
            Parent root = loader.load();

            AppShellController shellController = loader.getController();
            shellController.setDependencyInjector(dependencyInjector);

            Scene scene = new Scene(root, 1200, 720);
            stage.setTitle("Pico Possum - Minimalist POS");
            stage.setMinWidth(1024);
            stage.setMinHeight(768);
            stage.setScene(scene);
            stage.setResizable(true);
            dependencyInjector.getToastService().setMainStage(stage);
            stage.show();
            stage.centerOnScreen();

            LOGGER.info("Main application shell loaded");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load main application shell", ex);
        }
    }

    private void showLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(AppBootstrap.class.getResource("/fxml/auth/login.fxml"));
            
            loader.setControllerFactory(type -> {
                if (type == com.picopossum.ui.auth.LoginController.class) {
                    return new com.picopossum.ui.auth.LoginController(
                            serviceLocator.getAuthService(), 
                            serviceLocator.getUserService(), 
                            () -> loadMainShell(stage));
                }
                return dependencyInjector.getControllerFactory().call(type);
            });
            
            Parent root = loader.load();
            Scene scene = new Scene(root, 1000, 800);
            stage.setTitle("Pico Possum - Login");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            LOGGER.error("Failed to load login screen", e);
            throw new RuntimeException("Failed to load login screen", e);
        }
    }
}

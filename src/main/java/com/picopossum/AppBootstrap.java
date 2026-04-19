package com.picopossum;

import com.picopossum.application.ApplicationModule;
import com.picopossum.application.sales.SalesService;
import com.picopossum.ui.sales.ProductSearchIndex;
import com.picopossum.application.transactions.TransactionService;
import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.ui.DependencyInjector;
import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
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
import com.picopossum.infrastructure.security.PasswordHasher;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.ui.AppShellController;

public final class AppBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppBootstrap.class);

    private AppPaths appPaths;
    private DatabaseManager databaseManager;
    private DatabaseBackupService backupService;
    private ServiceLocator serviceLocator;
    private ApplicationModule applicationModule;
    private DependencyInjector dependencyInjector;
    private SalesService salesService;
    private ProductSearchIndex productSearchIndex;
    private TransactionService transactionService;
    private ReturnsService returnsService;
    private ReportsService reportsService;
    private SqliteSalesRepository salesRepository;
    private com.picopossum.domain.services.SaleCalculator saleCalculator;
    private com.picopossum.persistence.repositories.sqlite.SqliteAuditRepository auditRepository;
    private com.picopossum.application.auth.AuthService authService;
    private TransactionManager transactionManager;
    private com.picopossum.infrastructure.security.PasswordHasher passwordHasher;



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
        if (backupService != null) {
            backupService.stopDailyBackups();
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
        transactionManager = new TransactionManager(databaseManager);
        passwordHasher = new com.picopossum.infrastructure.security.PasswordHasher();

        com.picopossum.application.auth.ServiceSecurity.setAuditRepository(
                new com.picopossum.persistence.repositories.sqlite.SqliteAuditRepository(databaseManager));

        serviceLocator = new ServiceLocator(databaseManager, transactionManager, appPaths);
        backupService = serviceLocator.getDatabaseBackupService();
        backupService.startDailyBackups();
    }

    private void initializeApplication() {
        JsonService jsonService = new JsonService();
        SqliteUserRepository userRepository = new SqliteUserRepository(databaseManager);
        SqliteSessionRepository sessionRepository = new SqliteSessionRepository(databaseManager);

        SqliteProductRepository productRepository = new SqliteProductRepository(databaseManager);
        SqliteCategoryRepository categoryRepository = new SqliteCategoryRepository(databaseManager);
        SqliteInventoryRepository inventoryRepository = new SqliteInventoryRepository(databaseManager);
        SqliteProductFlowRepository productFlowRepository = new SqliteProductFlowRepository(databaseManager);
        auditRepository = new SqliteAuditRepository(databaseManager);

        com.picopossum.persistence.repositories.sqlite.SqliteCustomerRepository customerRepository =
                new com.picopossum.persistence.repositories.sqlite.SqliteCustomerRepository(databaseManager);

        applicationModule = new ApplicationModule(
                userRepository, sessionRepository, productRepository,
                categoryRepository, inventoryRepository, productFlowRepository, auditRepository,
                customerRepository, transactionManager, passwordHasher, jsonService, appPaths,
                serviceLocator.getSettingsStore(), databaseManager
        );

        authService = new com.picopossum.application.auth.AuthService(userRepository, passwordHasher);

        salesRepository = new SqliteSalesRepository(databaseManager);
        com.picopossum.persistence.repositories.sqlite.SqliteTransactionRepository transactionRepo =
                new com.picopossum.persistence.repositories.sqlite.SqliteTransactionRepository(databaseManager);
        com.picopossum.persistence.repositories.sqlite.SqliteReturnsRepository returnRepository =
                new com.picopossum.persistence.repositories.sqlite.SqliteReturnsRepository(databaseManager);

        com.picopossum.application.sales.PaymentService paymentService =
                new com.picopossum.application.sales.PaymentService(salesRepository);
        com.picopossum.application.sales.InvoiceNumberService invoiceNumberService =
                new com.picopossum.application.sales.InvoiceNumberService(salesRepository);
        saleCalculator = new com.picopossum.domain.services.SaleCalculator();
        salesService = new SalesService(salesRepository, productRepository,
                customerRepository, auditRepository, applicationModule.getInventoryService(),
                saleCalculator, paymentService, transactionManager, jsonService, serviceLocator.getSettingsStore(),
                invoiceNumberService);

        productSearchIndex = new ProductSearchIndex(productRepository);

        transactionService = new com.picopossum.application.transactions.TransactionServiceImpl(transactionRepo, salesRepository);

        com.picopossum.shared.util.TimeUtil.initialize(serviceLocator.getSettingsStore());
        com.picopossum.shared.util.CurrencyUtil.initialize(serviceLocator.getSettingsStore());
        returnsService = new ReturnsService(returnRepository, salesRepository,
                applicationModule.getInventoryService(), auditRepository, transactionManager, jsonService, new com.picopossum.domain.services.ReturnCalculator());
        com.picopossum.persistence.repositories.sqlite.SqliteReportsRepository reportsRepository =
                new com.picopossum.persistence.repositories.sqlite.SqliteReportsRepository(databaseManager);
        reportsService = new ReportsService(reportsRepository, productFlowRepository);
    }

    private void initializeUI() {
        dependencyInjector = new DependencyInjector(applicationModule, serviceLocator, salesService,
                saleCalculator, productSearchIndex, transactionService, returnsService,
                reportsService, salesRepository, appPaths, authService);

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
        try (java.sql.ResultSet rs = databaseManager.getConnection().createStatement().executeQuery("PRAGMA integrity_check")) {
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
        try {
            switch (action) {
                case "REINDEX":
                    databaseManager.getConnection().createStatement().execute("REINDEX");
                    Platform.runLater(() -> com.picopossum.ui.common.controls.NotificationService.success("Database indices rebuilt successfully."));
                    break;
                case "VACUUM":
                    databaseManager.getConnection().createStatement().execute("VACUUM");
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
                    return new com.picopossum.ui.auth.LoginController(authService, applicationModule.getUserService(), () -> {
                        loadMainShell(stage);
                    });
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

package com.possum.infrastructure.lazy;

import com.possum.infrastructure.backup.DatabaseBackupService;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.filesystem.UploadStore;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.infrastructure.monitoring.PerformanceMonitor;
import com.possum.application.taxes.TaxExemptionService;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.db.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceLocator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLocator.class);
    
    private final DatabaseManager databaseManager;
    private final TransactionManager transactionManager;
    
    private final LazyService<JsonService> jsonService;
    private final LazyService<SettingsStore> settingsStore;
    private final LazyService<UploadStore> uploadStore;
    private final LazyService<PrinterService> printerService;
    private final LazyService<DatabaseBackupService> databaseBackupService;
    private final LazyService<PerformanceMonitor> performanceMonitor;
    private final LazyService<TaxExemptionService> taxExemptionService;
    private final LazyService<com.possum.application.drafts.DraftService> draftService;
    
    public ServiceLocator(DatabaseManager databaseManager, TransactionManager transactionManager, AppPaths appPaths) {
        this.databaseManager = databaseManager;
        this.transactionManager = transactionManager;
        
        this.jsonService = new LazyService<>(() -> {
            LOGGER.debug("Initializing JsonService");
            return new JsonService();
        });
        
        this.settingsStore = new LazyService<>(() -> {
            LOGGER.debug("Initializing SettingsStore");
            return new SettingsStore(appPaths, jsonService.get());
        });
        
        this.uploadStore = new LazyService<>(() -> {
            LOGGER.debug("Initializing UploadStore");
            return new UploadStore(appPaths);
        });
        
        this.printerService = new LazyService<>(() -> {
            LOGGER.debug("Initializing PrinterService");
            return new PrinterService();
        });

        this.databaseBackupService = new LazyService<>(() -> {
            LOGGER.debug("Initializing DatabaseBackupService");
            return new DatabaseBackupService(appPaths, databaseManager);
        });
        
        this.performanceMonitor = new LazyService<>(() -> {
            LOGGER.debug("Initializing PerformanceMonitor");
            return new PerformanceMonitor();
        });
        
        this.taxExemptionService = new LazyService<>(() -> {
            LOGGER.debug("Initializing TaxExemptionService");
            var auditRepo = new com.possum.persistence.repositories.sqlite.SqliteAuditRepository(databaseManager);
            return new TaxExemptionService(
                new com.possum.persistence.repositories.sqlite.SqliteTaxExemptionRepository(databaseManager.getConnection()),
                new com.possum.persistence.repositories.sqlite.SqliteCustomerRepository(databaseManager),
                new com.possum.infrastructure.logging.AuditLogger(auditRepo)
            );
        });

        this.draftService = new LazyService<>(() -> {
            LOGGER.debug("Initializing DraftService");
            return new com.possum.application.drafts.DraftService(databaseManager, jsonService.get());
        });
    }
    
    public JsonService getJsonService() {
        return jsonService.get();
    }
    
    public SettingsStore getSettingsStore() {
        return settingsStore.get();
    }
    
    public UploadStore getUploadStore() {
        return uploadStore.get();
    }
    
    public PrinterService getPrinterService() {
        return printerService.get();
    }

    public DatabaseBackupService getDatabaseBackupService() {
        return databaseBackupService.get();
    }
    
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor.get();
    }
    
    public TaxExemptionService getTaxExemptionService() {
        return taxExemptionService.get();
    }
    
    public com.possum.application.drafts.DraftService getDraftService() {
        return draftService.get();
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }
}

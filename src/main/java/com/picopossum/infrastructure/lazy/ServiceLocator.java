package com.picopossum.infrastructure.lazy;

import com.picopossum.infrastructure.backup.DatabaseBackupService;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.filesystem.UploadStore;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.infrastructure.monitoring.PerformanceMonitor;
import com.picopossum.persistence.db.DatabaseManager;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.infrastructure.system.SystemInteropService;
import com.picopossum.infrastructure.system.AppExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.picopossum.domain.repositories.*;
import com.picopossum.persistence.repositories.sqlite.*;
import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.PaymentService;
import com.picopossum.application.sales.InvoiceNumberService;
import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.auth.AuthService;
import com.picopossum.application.people.UserService;
import com.picopossum.application.people.CustomerService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.audit.AuditService;
import com.picopossum.application.products.ProductService;
import com.picopossum.application.products.ProductModule;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.ui.sales.ProductSearchIndex;
import com.picopossum.infrastructure.security.PasswordHasher;
import com.picopossum.domain.services.SaleCalculator;
import com.picopossum.domain.services.ReturnCalculator;

public class ServiceLocator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceLocator.class);
    
    private final DatabaseManager databaseManager;
    private final TransactionManager transactionManager;
    private final AppPaths appPaths;
    
    private final LazyService<JsonService> jsonService;
    private final LazyService<SettingsStore> settingsStore;
    private final LazyService<UploadStore> uploadStore;
    private final LazyService<PrinterService> printerService;
    private final LazyService<DatabaseBackupService> databaseBackupService;
    private final LazyService<PerformanceMonitor> performanceMonitor;
    private final LazyService<com.picopossum.application.drafts.DraftService> draftService;
    private final LazyService<SystemInteropService> systemInteropService;
    private final LazyService<AppExecutor> appExecutor;
    
    // Repositories
    private final LazyService<UserRepository> userRepository;
    private final LazyService<SessionRepository> sessionRepository;
    private final LazyService<ProductRepository> productRepository;
    private final LazyService<CategoryRepository> categoryRepository;
    private final LazyService<InventoryRepository> inventoryRepository;
    private final LazyService<ProductFlowRepository> productFlowRepository;
    private final LazyService<AuditRepository> auditRepository;
    private final LazyService<CustomerRepository> customerRepository;
    private final LazyService<SalesRepository> salesRepository;
    private final LazyService<ReturnsRepository> returnsRepository;
    private final LazyService<ReportsRepository> reportsRepository;
    
    // Services
    private final LazyService<UserService> userService;
    private final LazyService<CustomerService> customerService;
    private final LazyService<AuditService> auditService;
    private final LazyService<ProductFlowService> productFlowService;
    private final LazyService<InventoryService> inventoryService;
    private final LazyService<ProductService> productService;
    private final LazyService<CategoryService> categoryService;
    private final LazyService<AuthService> authService;
    private final LazyService<SalesService> salesService;
    private final LazyService<ReturnsService> returnsService;
    private final LazyService<ReportsService> reportsService;
    
    // Domain & Utilities
    private final LazyService<PasswordHasher> passwordHasher;
    private final LazyService<ProductSearchIndex> productSearchIndex;
    private final LazyService<SaleCalculator> saleCalculator;
    private final LazyService<ReturnCalculator> returnCalculator;
    private final LazyService<PaymentService> paymentService;
    private final LazyService<InvoiceNumberService> invoiceNumberService;
    
    public ServiceLocator(DatabaseManager databaseManager, TransactionManager transactionManager, AppPaths appPaths) {
        this.databaseManager = databaseManager;
        this.transactionManager = transactionManager;
        this.appPaths = appPaths;
        
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
        
        this.draftService = new LazyService<>(() -> {
            LOGGER.debug("Initializing DraftService");
            return new com.picopossum.application.drafts.DraftService(databaseManager, jsonService.get());
        });
        
        this.systemInteropService = new LazyService<>(() -> {
            LOGGER.debug("Initializing SystemInteropService");
            return new SystemInteropService();
        });
        
        this.appExecutor = new LazyService<>(() -> {
            LOGGER.debug("Initializing AppExecutor");
            return new AppExecutor();
        });

        // Repositories Initialization
        this.userRepository = new LazyService<>(() -> new SqliteUserRepository(databaseManager, getPerformanceMonitor()));
        this.sessionRepository = new LazyService<>(() -> new SqliteSessionRepository(databaseManager));
        this.productRepository = new LazyService<>(() -> new SqliteProductRepository(databaseManager, getPerformanceMonitor()));
        this.categoryRepository = new LazyService<>(() -> new SqliteCategoryRepository(databaseManager, getPerformanceMonitor()));
        this.inventoryRepository = new LazyService<>(() -> new SqliteInventoryRepository(databaseManager, getPerformanceMonitor()));
        this.productFlowRepository = new LazyService<>(() -> new SqliteProductFlowRepository(databaseManager, getPerformanceMonitor()));
        this.auditRepository = new LazyService<>(() -> new SqliteAuditRepository(databaseManager, getPerformanceMonitor()));
        this.customerRepository = new LazyService<>(() -> new SqliteCustomerRepository(databaseManager, getPerformanceMonitor()));
        this.salesRepository = new LazyService<>(() -> new SqliteSalesRepository(databaseManager, getPerformanceMonitor()));
        this.returnsRepository = new LazyService<>(() -> new SqliteReturnsRepository(databaseManager, getPerformanceMonitor()));
        this.reportsRepository = new LazyService<>(() -> new SqliteReportsRepository(databaseManager));

        // Utilities
        this.passwordHasher = new LazyService<>(PasswordHasher::new);
        this.saleCalculator = new LazyService<>(SaleCalculator::new);
        this.returnCalculator = new LazyService<>(ReturnCalculator::new);
        this.invoiceNumberService = new LazyService<>(() -> new InvoiceNumberService(getSalesRepository()));
        this.paymentService = new LazyService<>(() -> new PaymentService(getSalesRepository()));
        this.productSearchIndex = new LazyService<>(() -> new ProductSearchIndex(getProductRepository()));

        // Services Initialization
        this.userService = new LazyService<>(() -> new UserService(getUserRepository(), getPasswordHasher()));
        this.customerService = new LazyService<>(() -> new CustomerService(getCustomerRepository()));
        this.auditService = new LazyService<>(() -> new AuditService(getAuditRepository(), getJsonService().getObjectMapper()));
        this.productFlowService = new LazyService<>(() -> new ProductFlowService(getProductFlowRepository()));
        this.categoryService = new LazyService<>(() -> new CategoryService(getCategoryRepository()));
        this.authService = new LazyService<>(() -> new AuthService(getUserRepository(), getPasswordHasher()));
        
        this.inventoryService = new LazyService<>(() -> new InventoryService(
                getInventoryRepository(), getProductFlowService(), getAuditService(),
                transactionManager, getJsonService(), getSettingsStore(), getProductSearchIndex()));

        this.productService = new LazyService<>(() -> {
            ProductModule mod = new ProductModule(
                getProductRepository(), getInventoryRepository(), getAuditService(),
                transactionManager, appPaths, getSettingsStore(), getProductSearchIndex());
            return mod.getProductService();
        });

        this.salesService = new LazyService<>(() -> new SalesService(
                getSalesRepository(), getProductRepository(), getCustomerRepository(),
                getAuditService(), getInventoryService(), getSaleCalculator(),
                getPaymentService(), transactionManager, getJsonService(),
                getSettingsStore(), getInvoiceNumberService(), getReturnsRepository()));

        this.returnsService = new LazyService<>(() -> new ReturnsService(
                getReturnsRepository(), getSalesRepository(), getInventoryService(),
                getAuditService(), transactionManager, getJsonService(), 
                getReturnCalculator(), getInvoiceNumberService()));

        this.reportsService = new LazyService<>(() -> new ReportsService(getReportsRepository(), getProductFlowRepository()));
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
    
    public com.picopossum.application.drafts.DraftService getDraftService() {
        return draftService.get();
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public AppPaths getAppPaths() {
        return appPaths;
    }

    public SystemInteropService getSystemInteropService() {
        return systemInteropService.get();
    }
    
    public AppExecutor getAppExecutor() {
        return appExecutor.get();
    }

    // Repositories Getters
    public UserRepository getUserRepository() { return userRepository.get(); }
    public SessionRepository getSessionRepository() { return sessionRepository.get(); }
    public ProductRepository getProductRepository() { return productRepository.get(); }
    public CategoryRepository getCategoryRepository() { return categoryRepository.get(); }
    public InventoryRepository getInventoryRepository() { return inventoryRepository.get(); }
    public ProductFlowRepository getProductFlowRepository() { return productFlowRepository.get(); }
    public AuditRepository getAuditRepository() { return auditRepository.get(); }
    public CustomerRepository getCustomerRepository() { return customerRepository.get(); }
    public SalesRepository getSalesRepository() { return salesRepository.get(); }
    public ReturnsRepository getReturnsRepository() { return returnsRepository.get(); }
    public ReportsRepository getReportsRepository() { return reportsRepository.get(); }

    // Services Getters
    public UserService getUserService() { return userService.get(); }
    public CustomerService getCustomerService() { return customerService.get(); }
    public AuditService getAuditService() { return auditService.get(); }
    public ProductFlowService getProductFlowService() { return productFlowService.get(); }
    public InventoryService getInventoryService() { return inventoryService.get(); }
    public ProductService getProductService() { return productService.get(); }
    public CategoryService getCategoryService() { return categoryService.get(); }
    public AuthService getAuthService() { return authService.get(); }
    public SalesService getSalesService() { return salesService.get(); }
    public ReturnsService getReturnsService() { return returnsService.get(); }
    public ReportsService getReportsService() { return reportsService.get(); }

    // Utilities Getters
    public PasswordHasher getPasswordHasher() { return passwordHasher.get(); }
    public ProductSearchIndex getProductSearchIndex() { return productSearchIndex.get(); }
    public SaleCalculator getSaleCalculator() { return saleCalculator.get(); }
    public ReturnCalculator getReturnCalculator() { return returnCalculator.get(); }
    public PaymentService getPaymentService() { return paymentService.get(); }
    public InvoiceNumberService getInvoiceNumberService() { return invoiceNumberService.get(); }
}

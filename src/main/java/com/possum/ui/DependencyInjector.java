package com.possum.ui;

import com.possum.application.ApplicationModule;
import com.possum.infrastructure.lazy.ServiceLocator;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.application.sales.SalesService;
import com.possum.ui.sales.ProductSearchIndex;
import com.possum.application.transactions.TransactionService;
import com.possum.application.returns.ReturnsService;
import com.possum.application.reports.ReportsService;
import com.possum.application.purchase.PurchaseService;
import com.possum.application.drafts.DraftService;
import com.possum.persistence.repositories.sqlite.SqlitePosDraftRepository;
import com.possum.domain.repositories.*;
import com.possum.ui.common.toast.ToastService;
import com.possum.ui.navigation.NavigationManager;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DependencyInjector {

    private final ApplicationModule applicationModule;
    private final ServiceLocator serviceLocator;

    private final SalesService salesService;
    private final com.possum.application.sales.TaxEngine taxEngine;
    private final com.possum.domain.services.SaleCalculator saleCalculator;
    private final ProductSearchIndex productSearchIndex;

    private final TransactionService transactionService;
    private final ReturnsService returnsService;
    private final ReportsService reportsService;
    private final PurchaseService purchaseService;

    private final VariantRepository variantRepository;
    private final SalesRepository salesRepository;
    private final SupplierRepository supplierRepository;
    private final TaxRepository taxRepository;
    private final com.possum.infrastructure.filesystem.AppPaths appPaths;

    private NavigationManager navigationManager;
    private com.possum.ui.workspace.WorkspaceManager workspaceManager;
    private final ToastService toastService = new ToastService();

    private final Map<Class<?>, Supplier<Object>> registry = new HashMap<>();

    public DependencyInjector(ApplicationModule applicationModule, ServiceLocator serviceLocator,
                              SalesService salesService, com.possum.application.sales.TaxEngine taxEngine,
                              com.possum.domain.services.SaleCalculator saleCalculator,
                              ProductSearchIndex productSearchIndex,
                              TransactionService transactionService, ReturnsService returnsService,
                              ReportsService reportsService, PurchaseService purchaseService,
                              VariantRepository variantRepository, SalesRepository salesRepository,
                              SupplierRepository supplierRepository, TaxRepository taxRepository, com.possum.infrastructure.filesystem.AppPaths appPaths) {

        this.applicationModule = applicationModule;
        this.serviceLocator = serviceLocator;
        this.salesService = salesService;
        this.taxEngine = taxEngine;
        this.saleCalculator = saleCalculator;
        this.productSearchIndex = productSearchIndex;

        this.transactionService = transactionService;
        this.returnsService = returnsService;
        this.reportsService = reportsService;
        this.purchaseService = purchaseService;
        this.variantRepository = variantRepository;
        this.salesRepository = salesRepository;
        this.supplierRepository = supplierRepository;
        this.taxRepository = taxRepository;
        this.appPaths = appPaths;
        registry.put(SqlitePosDraftRepository.class, () -> new SqlitePosDraftRepository(serviceLocator.getDatabaseManager(), variantRepository, serviceLocator.getTransactionManager()));
        buildRegistry();
    }

    private void buildRegistry() {
        // Application services
        registry.put(com.possum.application.products.ProductService.class, applicationModule::getProductService);
        registry.put(com.possum.application.variants.VariantService.class, applicationModule::getVariantService);
        registry.put(com.possum.application.categories.CategoryService.class, applicationModule::getCategoryService);
        registry.put(com.possum.application.inventory.InventoryService.class, applicationModule::getInventoryService);
        registry.put(com.possum.application.inventory.ProductFlowService.class, applicationModule::getProductFlowService);
        registry.put(com.possum.application.auth.AuthService.class, () -> applicationModule.getAuthModule().getAuthService());
        registry.put(com.possum.application.auth.AuthorizationService.class, com.possum.application.auth.AuthorizationService::new);
        registry.put(com.possum.application.audit.AuditService.class, applicationModule::getAuditService);
        registry.put(com.possum.application.people.UserService.class, applicationModule::getUserService);
        registry.put(com.possum.application.people.CustomerService.class, applicationModule::getCustomerService);
        registry.put(DraftService.class, applicationModule::getDraftService);
        registry.put(SalesService.class, () -> salesService);
        registry.put(com.possum.application.sales.TaxEngine.class, () -> taxEngine);
        registry.put(com.possum.domain.services.SaleCalculator.class, () -> saleCalculator);
        registry.put(ProductSearchIndex.class, () -> productSearchIndex);

        registry.put(TransactionService.class, () -> transactionService);
        registry.put(ReturnsService.class, () -> returnsService);
        registry.put(ReportsService.class, () -> reportsService);
        registry.put(PurchaseService.class, () -> purchaseService);

        // Repositories
        registry.put(VariantRepository.class, () -> variantRepository);
        registry.put(SalesRepository.class, () -> salesRepository);
        registry.put(SupplierRepository.class, () -> supplierRepository);
        registry.put(TaxRepository.class, () -> taxRepository);
        registry.put(SqlitePosDraftRepository.class, () -> new SqlitePosDraftRepository(serviceLocator.getDatabaseManager(), variantRepository, serviceLocator.getTransactionManager()));

        // Infrastructure
        registry.put(ToastService.class, () -> toastService);
        registry.put(com.possum.infrastructure.serialization.JsonService.class, serviceLocator::getJsonService);
        registry.put(com.possum.infrastructure.filesystem.SettingsStore.class, serviceLocator::getSettingsStore);
        registry.put(com.possum.infrastructure.printing.PrinterService.class, serviceLocator::getPrinterService);
        registry.put(com.possum.infrastructure.backup.DatabaseBackupService.class, serviceLocator::getDatabaseBackupService);
        registry.put(com.possum.infrastructure.filesystem.AppPaths.class, () -> appPaths);

        // UI
        registry.put(NavigationManager.class, () -> navigationManager);
        registry.put(com.possum.ui.workspace.WorkspaceManager.class, () -> workspaceManager);
        registry.put(com.possum.ui.auth.SessionStore.class,
                () -> new com.possum.ui.auth.SessionStore(appPaths, serviceLocator.getJsonService()));
        registry.put(DependencyInjector.class, () -> this);

        // Composite controllers
        registry.put(com.possum.ui.sales.SalesHistoryController.class,
                () -> new com.possum.ui.sales.SalesHistoryController(
                        salesService, serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager));
        registry.put(com.possum.ui.sales.SaleDetailController.class,
                () -> new com.possum.ui.sales.SaleDetailController(
                        salesService, workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), productSearchIndex));
        registry.put(com.possum.ui.products.ProductFormController.class,
                () -> new com.possum.ui.products.ProductFormController(
                        applicationModule.getProductService(), applicationModule.getCategoryService(),
                        taxRepository, workspaceManager, serviceLocator.getSettingsStore(), productSearchIndex,
                        serviceLocator.getDraftService()));
        registry.put(com.possum.ui.inventory.VariantsController.class,
                () -> new com.possum.ui.inventory.VariantsController(
                        variantRepository, applicationModule.getCategoryService(), taxRepository, workspaceManager));
        registry.put(com.possum.ui.returns.CreateReturnDialogController.class,
                () -> new com.possum.ui.returns.CreateReturnDialogController(
                        salesService, salesRepository, returnsService));
        registry.put(com.possum.ui.purchase.PurchaseOrderDetailController.class,
                () -> new com.possum.ui.purchase.PurchaseOrderDetailController(purchaseService, workspaceManager));
        registry.put(com.possum.ui.purchase.PurchaseOrderFormController.class,
                () -> new com.possum.ui.purchase.PurchaseOrderFormController(
                        purchaseService, supplierRepository, variantRepository,
                        workspaceManager, productSearchIndex, salesService));
        registry.put(com.possum.ui.purchase.PurchaseController.class,
                () -> new com.possum.ui.purchase.PurchaseController(purchaseService, salesService, workspaceManager));
        
        // Phase 6: Enhanced UI Controllers
        registry.put(com.possum.ui.taxes.TaxConfigurationController.class,
                () -> new com.possum.ui.taxes.TaxConfigurationController(
                        serviceLocator.getSettingsStore(), toastService));
        
        registry.put(com.possum.ui.dashboard.DashboardController.class,
                () -> new com.possum.ui.dashboard.DashboardController(
                        reportsService, applicationModule.getInventoryService(),
                        serviceLocator.getDatabaseBackupService()));

        registry.put(com.possum.ui.insights.BusinessInsightsController.class,
                () -> new com.possum.ui.insights.BusinessInsightsController(reportsService));
    }

    public com.possum.infrastructure.filesystem.AppPaths getAppPaths() {
        return appPaths;
    }

    public ApplicationModule getApplicationModule() {
        return applicationModule;
    }

    public javafx.util.Callback<Class<?>, Object> getControllerFactory() {
        return type -> {
            try {
                java.lang.reflect.Constructor<?>[] constructors = type.getConstructors();
                for (java.lang.reflect.Constructor<?> constructor : constructors) {
                    if (constructor.getParameterCount() > 0) {
                        try {
                            Object[] args = new Object[constructor.getParameterCount()];
                            Parameter[] parameters = constructor.getParameters();
                            boolean allResolved = true;
                            for (int i = 0; i < parameters.length; i++) {
                                args[i] = resolveDependency(parameters[i].getType());
                                if (args[i] == null) {
                                    allResolved = false;
                                    break;
                                }
                            }
                            if (allResolved) {
                                return constructor.newInstance(args);
                            }
                        } catch (Exception ignored) {
                            // Try next constructor
                        }
                    }
                }
                // Fallback to no-args constructor if available or found first
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                LoggingConfig.getLogger().error("DependencyInjector failed to instantiate controller of type {}: {}", type.getName(), e.getMessage(), e);
                return null;
            }
        };
    }

    public void setNavigationManager(NavigationManager navigationManager) {
        this.navigationManager = navigationManager;
        registry.put(NavigationManager.class, () -> navigationManager);
    }

    public void setWorkspaceManager(com.possum.ui.workspace.WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
        registry.put(com.possum.ui.workspace.WorkspaceManager.class, () -> workspaceManager);
        // Re-register composite controllers that depend on workspaceManager
        registry.put(com.possum.ui.sales.SalesHistoryController.class,
                () -> new com.possum.ui.sales.SalesHistoryController(
                        salesService, serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager));
        registry.put(com.possum.ui.sales.SaleDetailController.class,
                () -> new com.possum.ui.sales.SaleDetailController(
                        salesService, workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), productSearchIndex));
        registry.put(com.possum.ui.products.ProductFormController.class,
                () -> new com.possum.ui.products.ProductFormController(
                        applicationModule.getProductService(), applicationModule.getCategoryService(),
                        taxRepository, workspaceManager, serviceLocator.getSettingsStore(), productSearchIndex,
                        serviceLocator.getDraftService()));
        registry.put(com.possum.ui.inventory.VariantsController.class,
                () -> new com.possum.ui.inventory.VariantsController(
                        variantRepository, applicationModule.getCategoryService(), taxRepository, workspaceManager));
        registry.put(com.possum.ui.purchase.PurchaseOrderDetailController.class,
                () -> new com.possum.ui.purchase.PurchaseOrderDetailController(purchaseService, workspaceManager));
        registry.put(com.possum.ui.purchase.PurchaseOrderFormController.class,
                () -> new com.possum.ui.purchase.PurchaseOrderFormController(
                        purchaseService, supplierRepository, variantRepository,
                        workspaceManager, productSearchIndex, salesService));
        registry.put(com.possum.ui.purchase.PurchaseController.class,
                () -> new com.possum.ui.purchase.PurchaseController(purchaseService, salesService, workspaceManager));
    }

    public void injectDependencies(Object controller) {
        // Obsolete, left empty for compatibility if called explicitly elsewhere
    }

    public ToastService getToastService() {
        return toastService;
    }

    private Object resolveDependency(Class<?> type) {
        Supplier<Object> supplier = registry.get(type);
        if (supplier != null) {
            return supplier.get();
        }
        LoggingConfig.getLogger().error("Could not resolve dependency of type: {}", type.getName());
        return null;
    }
}

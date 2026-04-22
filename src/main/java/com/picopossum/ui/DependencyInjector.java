package com.picopossum.ui;

import com.picopossum.application.ApplicationModule;
import com.picopossum.infrastructure.lazy.ServiceLocator;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.application.sales.SalesService;
import com.picopossum.ui.sales.ProductSearchIndex;

import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.reports.ReportsService;
import com.picopossum.application.drafts.DraftService;
import com.picopossum.persistence.repositories.sqlite.SqlitePosDraftRepository;
import com.picopossum.domain.repositories.*;
import com.picopossum.ui.common.toast.ToastService;
import com.picopossum.ui.navigation.NavigationManager;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DependencyInjector {

    private final ApplicationModule applicationModule;
    private final ServiceLocator serviceLocator;

    private final SalesService salesService;
    private final com.picopossum.domain.services.SaleCalculator saleCalculator;
    private final ProductSearchIndex productSearchIndex;


    private final ReturnsService returnsService;
    private final com.picopossum.domain.services.ReturnCalculator returnCalculator;
    private final ReportsService reportsService;

    private final SalesRepository salesRepository;
    private final com.picopossum.infrastructure.filesystem.AppPaths appPaths;

    private NavigationManager navigationManager;
    private com.picopossum.ui.workspace.WorkspaceManager workspaceManager;
    private final ProductRepository productRepository;
    private final com.picopossum.application.auth.AuthService authService;
    private final ToastService toastService = new ToastService();

    private final Map<Class<?>, Supplier<Object>> registry = new HashMap<>();

    public DependencyInjector(ApplicationModule applicationModule, ServiceLocator serviceLocator,
                               SalesService salesService,
                               com.picopossum.domain.services.SaleCalculator saleCalculator,
                               ProductSearchIndex productSearchIndex,
                               ReturnsService returnsService,
                               com.picopossum.domain.services.ReturnCalculator returnCalculator,
                               ReportsService reportsService,
                               SalesRepository salesRepository,
                               ProductRepository productRepository,
                               com.picopossum.infrastructure.filesystem.AppPaths appPaths,
                               com.picopossum.application.auth.AuthService authService) {

        this.applicationModule = applicationModule;
        this.serviceLocator = serviceLocator;
        this.salesService = salesService;
        this.saleCalculator = saleCalculator;
        this.productSearchIndex = productSearchIndex;


        this.returnsService = returnsService;
        this.returnCalculator = returnCalculator;
        this.reportsService = reportsService;
        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
        this.appPaths = appPaths;
        this.authService = authService;
        buildRegistry();
    }

    private void buildRegistry() {
        // Application services
        registry.put(com.picopossum.application.products.ProductService.class, applicationModule::getProductService);
        registry.put(com.picopossum.application.categories.CategoryService.class, applicationModule::getCategoryService);
        registry.put(com.picopossum.application.inventory.InventoryService.class, applicationModule::getInventoryService);
        registry.put(com.picopossum.application.inventory.ProductFlowService.class, applicationModule::getProductFlowService);
        registry.put(com.picopossum.application.audit.AuditService.class, applicationModule::getAuditService);
        registry.put(com.picopossum.application.people.UserService.class, applicationModule::getUserService);
        registry.put(com.picopossum.application.people.CustomerService.class, applicationModule::getCustomerService);
        registry.put(com.picopossum.application.auth.AuthService.class, () -> authService);
        registry.put(DraftService.class, applicationModule::getDraftService);
        registry.put(SalesService.class, () -> salesService);
        registry.put(com.picopossum.domain.services.SaleCalculator.class, () -> saleCalculator);
        registry.put(ProductSearchIndex.class, () -> productSearchIndex);


        registry.put(ReturnsService.class, () -> returnsService);
        registry.put(com.picopossum.domain.services.ReturnCalculator.class, () -> returnCalculator);
        registry.put(ReportsService.class, () -> reportsService);

        // Repositories
        registry.put(SalesRepository.class, () -> salesRepository);
        registry.put(ProductRepository.class, () -> productRepository);
        registry.put(SqlitePosDraftRepository.class, () -> new SqlitePosDraftRepository(serviceLocator.getDatabaseManager(), productRepository, serviceLocator.getTransactionManager()));

        // Infrastructure
        registry.put(ToastService.class, () -> toastService);
        registry.put(com.picopossum.infrastructure.serialization.JsonService.class, serviceLocator::getJsonService);
        registry.put(com.picopossum.infrastructure.filesystem.SettingsStore.class, serviceLocator::getSettingsStore);
        registry.put(com.picopossum.infrastructure.printing.PrinterService.class, serviceLocator::getPrinterService);
        registry.put(com.picopossum.infrastructure.backup.DatabaseBackupService.class, serviceLocator::getDatabaseBackupService);
        registry.put(com.picopossum.infrastructure.filesystem.AppPaths.class, () -> appPaths);
        registry.put(com.picopossum.infrastructure.filesystem.UploadStore.class, serviceLocator::getUploadStore);
        registry.put(com.picopossum.infrastructure.system.SystemInteropService.class, serviceLocator::getSystemInteropService);
        registry.put(com.picopossum.infrastructure.monitoring.PerformanceMonitor.class, serviceLocator::getPerformanceMonitor);

        // UI
        registry.put(NavigationManager.class, () -> navigationManager);
        registry.put(com.picopossum.ui.workspace.WorkspaceManager.class, () -> workspaceManager);
        registry.put(DependencyInjector.class, () -> this);

        // Composite controllers
        registry.put(com.picopossum.ui.sales.SalesHistoryController.class,
                () -> new com.picopossum.ui.sales.SalesHistoryController(
                        salesService, serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager));
        registry.put(com.picopossum.ui.sales.SaleDetailController.class,
                () -> new com.picopossum.ui.sales.SaleDetailController(
                        salesService, workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), productSearchIndex));
        registry.put(com.picopossum.ui.products.ProductFormController.class,
                () -> new com.picopossum.ui.products.ProductFormController(
                        applicationModule.getProductService(), applicationModule.getCategoryService(),
                        workspaceManager, serviceLocator.getSettingsStore(), productSearchIndex,
                        serviceLocator.getDraftService()));
        registry.put(com.picopossum.ui.returns.CreateReturnDialogController.class,
                () -> new com.picopossum.ui.returns.CreateReturnDialogController(
                        salesService, salesRepository, returnsService, returnCalculator));
        registry.put(com.picopossum.ui.inventory.InventoryController.class,
                () -> new com.picopossum.ui.inventory.InventoryController(
                        applicationModule.getInventoryService(), productRepository,
                        applicationModule.getCategoryService(), workspaceManager));
        registry.put(com.picopossum.ui.dashboard.DashboardController.class,
                () -> new com.picopossum.ui.dashboard.DashboardController(
                        reportsService, applicationModule.getInventoryService(),
                        serviceLocator.getDatabaseBackupService(),
                        serviceLocator.getPerformanceMonitor()));
        registry.put(com.picopossum.ui.insights.BusinessInsightsController.class,
                () -> new com.picopossum.ui.insights.BusinessInsightsController(reportsService));
    }

    public com.picopossum.infrastructure.filesystem.AppPaths getAppPaths() {
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

    public void setWorkspaceManager(com.picopossum.ui.workspace.WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
        registry.put(com.picopossum.ui.workspace.WorkspaceManager.class, () -> workspaceManager);
        // Re-register composite controllers that depend on workspaceManager
        registry.put(com.picopossum.ui.sales.SalesHistoryController.class,
                () -> new com.picopossum.ui.sales.SalesHistoryController(
                        salesService, serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager));
        registry.put(com.picopossum.ui.sales.SaleDetailController.class,
                () -> new com.picopossum.ui.sales.SaleDetailController(
                        salesService, workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), productSearchIndex));
        registry.put(com.picopossum.ui.products.ProductFormController.class,
                () -> new com.picopossum.ui.products.ProductFormController(
                        applicationModule.getProductService(), applicationModule.getCategoryService(),
                        workspaceManager, serviceLocator.getSettingsStore(), productSearchIndex,
                        serviceLocator.getDraftService()));
        registry.put(com.picopossum.ui.inventory.InventoryController.class,
                () -> new com.picopossum.ui.inventory.InventoryController(
                        applicationModule.getInventoryService(), productRepository,
                        applicationModule.getCategoryService(), workspaceManager));
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

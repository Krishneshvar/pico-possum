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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DependencyInjector {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyInjector.class);

    private final ServiceLocator serviceLocator;
    private NavigationManager navigationManager;
    private com.picopossum.ui.workspace.WorkspaceManager workspaceManager;
    private final com.picopossum.infrastructure.filesystem.AppPaths appPaths;
    private final ToastService toastService = new ToastService();

    private final Map<Class<?>, Supplier<Object>> registry = new HashMap<>();

    public DependencyInjector(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
        this.appPaths = serviceLocator.getAppPaths();
        buildRegistry();
    }

    private void buildRegistry() {
        // Application services
        registry.put(com.picopossum.application.products.ProductService.class, serviceLocator::getProductService);
        registry.put(com.picopossum.application.categories.CategoryService.class, serviceLocator::getCategoryService);
        registry.put(com.picopossum.application.inventory.InventoryService.class, serviceLocator::getInventoryService);
        registry.put(com.picopossum.application.inventory.ProductFlowService.class, serviceLocator::getProductFlowService);
        registry.put(com.picopossum.application.audit.AuditService.class, serviceLocator::getAuditService);
        registry.put(com.picopossum.application.people.UserService.class, serviceLocator::getUserService);
        registry.put(com.picopossum.application.people.CustomerService.class, serviceLocator::getCustomerService);
        registry.put(com.picopossum.application.auth.AuthService.class, serviceLocator::getAuthService);
        registry.put(DraftService.class, serviceLocator::getDraftService);
        registry.put(SalesService.class, serviceLocator::getSalesService);
        registry.put(com.picopossum.domain.services.SaleCalculator.class, serviceLocator::getSaleCalculator);
        registry.put(ProductSearchIndex.class, serviceLocator::getProductSearchIndex);


        registry.put(ReturnsService.class, serviceLocator::getReturnsService);
        registry.put(com.picopossum.domain.services.ReturnCalculator.class, serviceLocator::getReturnCalculator);
        registry.put(ReportsService.class, serviceLocator::getReportsService);

        // Repositories
        registry.put(SalesRepository.class, serviceLocator::getSalesRepository);
        registry.put(ProductRepository.class, serviceLocator::getProductRepository);
        registry.put(SqlitePosDraftRepository.class, () -> new SqlitePosDraftRepository(serviceLocator.getDatabaseManager(), serviceLocator.getProductRepository(), serviceLocator.getTransactionManager()));

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
        registry.put(com.picopossum.infrastructure.system.AppExecutor.class, serviceLocator::getAppExecutor);

        // UI
        registry.put(NavigationManager.class, () -> navigationManager);
        registry.put(com.picopossum.ui.workspace.WorkspaceManager.class, () -> workspaceManager);
        registry.put(DependencyInjector.class, () -> this);

        // Composite controllers
        registry.put(com.picopossum.ui.sales.SalesHistoryController.class,
                () -> new com.picopossum.ui.sales.SalesHistoryController(
                        serviceLocator.getSalesService(), serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager, serviceLocator.getAppExecutor()));
        registry.put(com.picopossum.ui.sales.SaleDetailController.class,
                () -> new com.picopossum.ui.sales.SaleDetailController(
                        serviceLocator.getSalesService(), workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), serviceLocator.getProductSearchIndex()));
        registry.put(com.picopossum.ui.products.ProductFormController.class,
                () -> new com.picopossum.ui.products.ProductFormController(
                        serviceLocator.getProductService(), serviceLocator.getCategoryService(),
                        workspaceManager, serviceLocator.getSettingsStore(), serviceLocator.getProductSearchIndex(),
                        serviceLocator.getDraftService()));
        registry.put(com.picopossum.ui.returns.CreateReturnDialogController.class,
                () -> new com.picopossum.ui.returns.CreateReturnDialogController(
                        serviceLocator.getSalesService(), serviceLocator.getSalesRepository(), serviceLocator.getReturnsService(), serviceLocator.getReturnCalculator()));
        registry.put(com.picopossum.ui.inventory.InventoryController.class,
                () -> new com.picopossum.ui.inventory.InventoryController(
                        serviceLocator.getInventoryService(), serviceLocator.getProductRepository(),
                        serviceLocator.getCategoryService(), workspaceManager, serviceLocator.getAppExecutor()));
        registry.put(com.picopossum.ui.dashboard.DashboardController.class,
                () -> new com.picopossum.ui.dashboard.DashboardController(
                        serviceLocator.getReportsService(), serviceLocator.getInventoryService(),
                        serviceLocator.getDatabaseBackupService(),
                        serviceLocator.getPerformanceMonitor()));
        registry.put(com.picopossum.ui.insights.BusinessInsightsController.class,
                () -> new com.picopossum.ui.insights.BusinessInsightsController(serviceLocator.getReportsService()));
    }

    public com.picopossum.infrastructure.filesystem.AppPaths getAppPaths() {
        return appPaths;
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
                LOGGER.error("DependencyInjector failed to instantiate controller of type {}: {}", type.getName(), e.getMessage(), e);
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
                        serviceLocator.getSalesService(), serviceLocator.getSettingsStore(),
                        serviceLocator.getPrinterService(), workspaceManager, serviceLocator.getAppExecutor()));
        registry.put(com.picopossum.ui.sales.SaleDetailController.class,
                () -> new com.picopossum.ui.sales.SaleDetailController(
                        serviceLocator.getSalesService(), workspaceManager,
                        serviceLocator.getSettingsStore(), serviceLocator.getPrinterService(), serviceLocator.getProductSearchIndex()));
        registry.put(com.picopossum.ui.products.ProductFormController.class,
                () -> new com.picopossum.ui.products.ProductFormController(
                        serviceLocator.getProductService(), serviceLocator.getCategoryService(),
                        workspaceManager, serviceLocator.getSettingsStore(), serviceLocator.getProductSearchIndex(),
                        serviceLocator.getDraftService()));
        registry.put(com.picopossum.ui.inventory.InventoryController.class,
                () -> new com.picopossum.ui.inventory.InventoryController(
                        serviceLocator.getInventoryService(), serviceLocator.getProductRepository(),
                        serviceLocator.getCategoryService(), workspaceManager, serviceLocator.getAppExecutor()));
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
        LOGGER.error("Could not resolve dependency of type: {}", type.getName());
        return null;
    }
}

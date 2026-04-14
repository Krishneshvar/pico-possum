package com.possum.application;

import com.possum.application.audit.AuditService;
import com.possum.application.auth.AuthModule;
import com.possum.application.categories.CategoryService;
import com.possum.application.inventory.InventoryService;
import com.possum.application.inventory.ProductFlowService;
import com.possum.application.products.ProductModule;
import com.possum.application.products.ProductService;
import com.possum.application.variants.VariantService;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.security.PasswordHasher;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.domain.services.StockManager;
import com.possum.application.people.UserService;
import com.possum.application.people.CustomerService;
import com.possum.application.drafts.DraftService;
import com.possum.persistence.db.ConnectionProvider;

public final class ApplicationModule {
    private final AuthModule authModule;
    private final ProductModule productModule;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;
    private final ProductFlowService productFlowService;
    private final AuditService auditService;
    private final UserService userService;
    private final CustomerService customerService;
    private final DraftService draftService;

    public ApplicationModule(UserRepository userRepository,
                            SessionRepository sessionRepository,
                            ProductRepository productRepository,
                            VariantRepository variantRepository,
                            CategoryRepository categoryRepository,
                            InventoryRepository inventoryRepository,
                            ProductFlowRepository productFlowRepository,
                            AuditRepository auditRepository,
                            CustomerRepository customerRepository,
                            TransactionManager transactionManager,
                            PasswordHasher passwordHasher,
                            JsonService jsonService,
                            AppPaths appPaths,
                            SettingsStore settingsStore,
                            ConnectionProvider connectionProvider) {
        this.authModule = new AuthModule(userRepository, sessionRepository, transactionManager, passwordHasher);
        this.userService = new com.possum.application.people.UserService(userRepository, passwordHasher);
        this.customerService = new com.possum.application.people.CustomerService(customerRepository);
        
        this.auditService = new AuditService(auditRepository, jsonService.getObjectMapper());
        
        this.productFlowService = new ProductFlowService(productFlowRepository);
        
        this.inventoryService = new InventoryService(
                inventoryRepository,
                productFlowService,
                auditRepository,
                transactionManager,
                jsonService,
                settingsStore,
                new StockManager()
        );
        
        this.productModule = new ProductModule(
                productRepository,
                variantRepository,
                inventoryRepository,
                auditRepository,
                transactionManager,
                appPaths,
                settingsStore
        );
        
        this.categoryService = new CategoryService(categoryRepository);
        this.draftService = new DraftService(connectionProvider, jsonService);
    }

    public AuthModule getAuthModule() {
        return authModule;
    }

    public ProductService getProductService() {
        return productModule.getProductService();
    }

    public VariantService getVariantService() {
        return productModule.getVariantService();
    }

    public CategoryService getCategoryService() {
        return categoryService;
    }

    public InventoryService getInventoryService() {
        return inventoryService;
    }

    public ProductFlowService getProductFlowService() {
        return productFlowService;
    }
    
    public AuditService getAuditService() {
        return auditService;
    }

    public com.possum.application.people.UserService getUserService() {
        return userService;
    }

    public com.possum.application.people.CustomerService getCustomerService() {
        return customerService;
    }

    public DraftService getDraftService() {
        return draftService;
    }
}

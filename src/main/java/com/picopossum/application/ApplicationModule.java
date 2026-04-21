package com.picopossum.application;

import com.picopossum.application.audit.AuditService;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.inventory.ProductFlowService;
import com.picopossum.application.products.ProductModule;
import com.picopossum.application.products.ProductService;
import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.security.PasswordHasher;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.*;
import com.picopossum.application.people.UserService;
import com.picopossum.application.people.CustomerService;
import com.picopossum.application.drafts.DraftService;
import com.picopossum.persistence.db.ConnectionProvider;

public final class ApplicationModule {
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
        this.userService = new com.picopossum.application.people.UserService(userRepository, passwordHasher);
        this.customerService = new com.picopossum.application.people.CustomerService(customerRepository);
        
        this.auditService = new AuditService(auditRepository, jsonService.getObjectMapper());
        
        this.productFlowService = new ProductFlowService(productFlowRepository);
        
        this.inventoryService = new InventoryService(
                inventoryRepository,
                productFlowService,
                auditRepository,
                transactionManager,
                jsonService,
                settingsStore
        );
        
        this.productModule = new ProductModule(
                productRepository,
                inventoryRepository,
                auditRepository,
                transactionManager,
                appPaths,
                settingsStore
        );
        
        this.categoryService = new CategoryService(categoryRepository);
        this.draftService = new DraftService(connectionProvider, jsonService);
    }

    public ProductService getProductService() {
        return productModule.getProductService();
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

    public com.picopossum.application.people.UserService getUserService() {
        return userService;
    }

    public com.picopossum.application.people.CustomerService getCustomerService() {
        return customerService;
    }

    public DraftService getDraftService() {
        return draftService;
    }
}

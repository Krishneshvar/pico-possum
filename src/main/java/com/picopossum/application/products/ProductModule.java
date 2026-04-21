package com.picopossum.application.products;

import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.FileStorageService;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.domain.repositories.ProductRepository;

/**
 * Inversion of Control (IoC) module for the Products domain.
 * Centralizes dependency management for the products module.
 */
public class ProductModule {
    private final ProductService productService;

    public ProductModule(ProductRepository productRepository,
                         InventoryRepository inventoryRepository,
                         AuditRepository auditRepository,
                         TransactionManager transactionManager,
                         AppPaths appPaths,
                         SettingsStore settingsStore) {

        ProductValidator validator = new ProductValidator();
        FileStorageService storageService = new FileStorageService(appPaths);
        com.picopossum.infrastructure.serialization.JsonService jsonService = new com.picopossum.infrastructure.serialization.JsonService();

        this.productService = new ProductService(
                productRepository,
                inventoryRepository,
                auditRepository,
                transactionManager,
                settingsStore,
                validator,
                storageService,
                jsonService
        );
    }

    public ProductService getProductService() {
        return productService;
    }
}

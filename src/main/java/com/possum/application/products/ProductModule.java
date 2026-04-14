package com.possum.application.products;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.domain.repositories.ProductRepository;

public class ProductModule {
    private final ProductService productService;

    public ProductModule(ProductRepository productRepository,
                         InventoryRepository inventoryRepository,
                         AuditRepository auditRepository,
                         TransactionManager transactionManager,
                         AppPaths appPaths,
                         SettingsStore settingsStore) {

        this.productService = new ProductService(
                productRepository,
                inventoryRepository,
                auditRepository,
                transactionManager,
                appPaths,
                settingsStore
        );
    }

    public ProductService getProductService() {
        return productService;
    }
}

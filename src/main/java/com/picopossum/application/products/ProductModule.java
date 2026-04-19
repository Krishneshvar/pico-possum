package com.picopossum.application.products;

import com.picopossum.infrastructure.filesystem.AppPaths;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.domain.repositories.InventoryRepository;
import com.picopossum.domain.repositories.ProductRepository;

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

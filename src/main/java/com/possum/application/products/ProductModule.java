package com.possum.application.products;

import com.possum.application.variants.VariantService;
import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.InventoryRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.domain.repositories.VariantRepository;

public class ProductModule {
    private final ProductService productService;
    private final VariantService variantService;

    public ProductModule(ProductRepository productRepository,
                         VariantRepository variantRepository,
                         InventoryRepository inventoryRepository,
                         AuditRepository auditRepository,
                         TransactionManager transactionManager,
                         AppPaths appPaths,
                         SettingsStore settingsStore) {
        this.variantService = new VariantService(
                variantRepository,
                inventoryRepository,
                auditRepository,
                transactionManager
        );

        this.productService = new ProductService(
                productRepository,
                variantService,
                variantRepository,
                auditRepository,
                transactionManager,
                appPaths,
                settingsStore
        );
    }

    public ProductService getProductService() {
        return productService;
    }

    public VariantService getVariantService() {
        return variantService;
    }
}

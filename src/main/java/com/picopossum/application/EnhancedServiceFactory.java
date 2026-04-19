package com.picopossum.application;

import com.picopossum.application.auth.*;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.sales.*;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.logging.AuditLogger;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.*;

import java.sql.Connection;

/**
 * Factory for creating enhanced service instances with proper dependency injection.
 * Integrates Phase 1-3 enhancements into the application layer.
 */
public class EnhancedServiceFactory {
    private final Connection connection;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;
    private final AuditLogger auditLogger;

    // Repositories
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    // Services
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final InvoiceNumberService invoiceNumberService;

    public EnhancedServiceFactory(
            Connection connection,
            TransactionManager transactionManager,
            JsonService jsonService,
            SettingsStore settingsStore,
            SalesRepository salesRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            AuditRepository auditRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            InvoiceNumberService invoiceNumberService
    ) {
        this.connection = connection;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.invoiceNumberService = invoiceNumberService;
        this.auditLogger = new AuditLogger(auditRepository);
    }

    /**
     * Creates EnhancedSalesService.
     */
    public EnhancedSalesService createEnhancedSalesService() {
        return new EnhancedSalesService(
                salesRepository,
                productRepository,
                customerRepository,
                inventoryService,
                paymentService,
                transactionManager,
                jsonService,
                settingsStore,
                invoiceNumberService,
                auditLogger
        );
    }

    /**
     * Gets the AuditLogger instance.
     */
    public AuditLogger getAuditLogger() {
        return auditLogger;
    }
}

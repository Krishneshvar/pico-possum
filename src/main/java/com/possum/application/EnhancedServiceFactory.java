package com.possum.application;

import com.possum.application.auth.*;
import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.*;
import com.possum.application.sales.config.TaxConfiguration;
import com.possum.application.taxes.TaxExemptionService;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.AuditLogger;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;

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
    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final TaxRepository taxRepository;
    private final TaxExemptionRepository taxExemptionRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

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
            VariantRepository variantRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            TaxRepository taxRepository,
            TaxExemptionRepository taxExemptionRepository,
            UserRepository userRepository,
            SessionRepository sessionRepository,
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
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.taxRepository = taxRepository;
        this.taxExemptionRepository = taxExemptionRepository;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.invoiceNumberService = invoiceNumberService;
        this.auditLogger = new AuditLogger(auditRepository);
    }

    /**
     * Creates EnhancedTaxEngine with default configuration (INVOICE_LEVEL rounding).
     */
    public EnhancedTaxEngine createEnhancedTaxEngine() {
        return new EnhancedTaxEngine(taxRepository, jsonService, TaxConfiguration.defaultConfig());
    }

    /**
     * Creates EnhancedTaxEngine with custom configuration.
     */
    public EnhancedTaxEngine createEnhancedTaxEngine(TaxConfiguration config) {
        return new EnhancedTaxEngine(taxRepository, jsonService, config);
    }

    /**
     * Creates EnhancedSalesService with EnhancedTaxEngine integration.
     */
    public EnhancedSalesService createEnhancedSalesService() {
        EnhancedTaxEngine taxEngine = createEnhancedTaxEngine();
        
        return new EnhancedSalesService(
                salesRepository,
                variantRepository,
                productRepository,
                customerRepository,
                inventoryService,
                taxEngine,
                paymentService,
                transactionManager,
                jsonService,
                settingsStore,
                invoiceNumberService,
                auditLogger
        );
    }

    /**
     * Creates TaxExemptionService for managing tax exemptions.
     */
    public TaxExemptionService createTaxExemptionService() {
        return new TaxExemptionService(
                taxExemptionRepository,
                customerRepository,
                auditLogger
        );
    }

    /**
     * Creates ConfigurableAuthorizationService with role hierarchy.
     */
    public ConfigurableAuthorizationService createConfigurableAuthorizationService() {
        return new ConfigurableAuthorizationService();
    }

    /**
     * Creates SessionService with enhanced security features.
     */
    public SessionService createSessionService() {
        return new SessionService(sessionRepository, userRepository);
    }

    /**
     * Creates LoginAttemptService for tracking login attempts.
     */
    public LoginAttemptService createLoginAttemptService() {
        return new LoginAttemptService();
    }

    /**
     * Creates SessionCleanupScheduler for automated session cleanup.
     */
    public SessionCleanupScheduler createSessionCleanupScheduler() {
        return new SessionCleanupScheduler(sessionRepository);
    }

    /**
     * Gets the AuditLogger instance.
     */
    public AuditLogger getAuditLogger() {
        return auditLogger;
    }
}

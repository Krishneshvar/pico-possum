package com.possum.application.sales;

import com.possum.application.inventory.InventoryService;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;

public class SalesModule {
    private final SalesService salesService;
    private final PaymentService paymentService;

    public SalesModule(SalesRepository salesRepository,
                       ProductRepository productRepository,
                       CustomerRepository customerRepository,
                       AuditRepository auditRepository,
                       InventoryService inventoryService,
                       TransactionManager transactionManager,
                       JsonService jsonService,
                       SettingsStore settingsStore) {
        
        this.paymentService = new PaymentService(salesRepository);
        InvoiceNumberService invoiceNumberService = new InvoiceNumberService(salesRepository);
        com.possum.domain.services.SaleCalculator saleCalculator = new com.possum.domain.services.SaleCalculator();
        
        this.salesService = new SalesService(
                salesRepository,
                productRepository,
                customerRepository,
                auditRepository,
                inventoryService,
                saleCalculator,
                paymentService,
                transactionManager,
                jsonService,
                settingsStore,
                invoiceNumberService
        );

    }

    public SalesService getSalesService() {
        return salesService;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }
}

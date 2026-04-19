package com.picopossum.application.sales;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.*;

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
        com.picopossum.domain.services.SaleCalculator saleCalculator = new com.picopossum.domain.services.SaleCalculator();
        
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

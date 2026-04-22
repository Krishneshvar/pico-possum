package com.picopossum.application.returns;

import com.picopossum.domain.services.ReturnCalculator;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.repositories.ReturnsRepository;
import com.picopossum.domain.repositories.SalesRepository;
import com.picopossum.application.sales.InvoiceNumberService;

public class ReturnsModule {
    private final ReturnCalculator returnCalculator;
    private final ReturnsService returnsService;

    public ReturnsModule(ReturnsRepository returnsRepository,
                         SalesRepository salesRepository,
                         InventoryService inventoryService,
                         AuditService auditService,
                         TransactionManager transactionManager,
                         JsonService jsonService,
                         InvoiceNumberService invoiceNumberService) {
        this.returnCalculator = new ReturnCalculator();
        this.returnsService = new ReturnsService(
                returnsRepository,
                salesRepository,
                inventoryService,
                auditService,
                transactionManager,
                jsonService,
                returnCalculator,
                invoiceNumberService
        );
    }

    public ReturnsService getReturnsService() {
        return returnsService;
    }

    public ReturnCalculator getReturnCalculator() {
        return returnCalculator;
    }
}

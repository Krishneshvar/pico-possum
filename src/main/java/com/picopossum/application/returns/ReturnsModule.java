package com.picopossum.application.returns;

import com.picopossum.domain.services.ReturnCalculator;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.AuditRepository;
import com.picopossum.domain.repositories.ReturnsRepository;
import com.picopossum.domain.repositories.SalesRepository;

public class ReturnsModule {
    private final ReturnsService returnsService;

    public ReturnsModule(ReturnsRepository returnsRepository,
                         SalesRepository salesRepository,
                         InventoryService inventoryService,
                         AuditRepository auditRepository,
                         TransactionManager transactionManager,
                         JsonService jsonService) {
        this.returnsService = new ReturnsService(
                returnsRepository,
                salesRepository,
                inventoryService,
                auditRepository,
                transactionManager,
                jsonService,
                new ReturnCalculator()
        );
    }

    public ReturnsService getReturnsService() {
        return returnsService;
    }
}

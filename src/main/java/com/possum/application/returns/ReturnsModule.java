package com.possum.application.returns;

import com.possum.domain.services.ReturnCalculator;

import com.possum.application.inventory.InventoryService;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.ReturnsRepository;
import com.possum.domain.repositories.SalesRepository;

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

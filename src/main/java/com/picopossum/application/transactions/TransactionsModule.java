package com.picopossum.application.transactions;

import com.picopossum.domain.repositories.SalesRepository;
import com.picopossum.domain.repositories.TransactionRepository;

public final class TransactionsModule {

    private final TransactionService transactionService;

    public TransactionsModule(
            TransactionRepository transactionRepository,
            SalesRepository salesRepository
    ) {
        this.transactionService = new TransactionServiceImpl(
                transactionRepository,
                salesRepository
        );
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }
}

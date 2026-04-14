package com.possum.application.transactions;

import com.possum.domain.repositories.SalesRepository;
import com.possum.domain.repositories.TransactionRepository;

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

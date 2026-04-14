package com.possum.application.transactions;

import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransactionService {

    /**
     * Get transactions with pagination and filters.
     */
    PagedResult<Transaction> getTransactions(TransactionFilter filter, Set<String> userPermissions);

    /**
     * Get a transaction by ID
     */
    Optional<Transaction> getTransactionById(long id, Set<String> userPermissions);

    /**
     * List all transactions for a sale
     */
    List<Transaction> listTransactionsBySale(long saleId, Set<String> userPermissions);
}

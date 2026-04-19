package com.picopossum.application.transactions;

import com.picopossum.domain.model.Transaction;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.TransactionFilter;

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

package com.picopossum.application.transactions;

import com.picopossum.domain.model.Transaction;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.TransactionFilter;

import java.util.List;
import java.util.Optional;

public interface TransactionService {

    /**
     * Get transactions with pagination and filters.
     */
    PagedResult<Transaction> getTransactions(TransactionFilter filter);

    /**
     * Get a transaction by ID
     */
    Optional<Transaction> getTransactionById(long id);

    /**
     * List all transactions for a sale
     */
    List<Transaction> listTransactionsBySale(long saleId);
}

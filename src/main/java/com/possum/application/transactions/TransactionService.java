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
     * Requires one of: reports.view, sales.view, sales.create
     *
     * @param filter Query parameters
     * @param userPermissions User's permission set
     * @return Paginated transactions
     */
    PagedResult<Transaction> getTransactions(TransactionFilter filter, Set<String> userPermissions);

    /**
     * Get a transaction by ID
     *
     * @param id Transaction ID
     * @param userPermissions User's permission set
     * @return Transaction if found
     */
    Optional<Transaction> getTransactionById(long id, Set<String> userPermissions);

    /**
     * List all transactions for a sale
     *
     * @param saleId Sale ID
     * @param userPermissions User's permission set
     * @return List of transactions
     */
    List<Transaction> listTransactionsBySale(long saleId, Set<String> userPermissions);

    /**
     * List all transactions for a purchase order
     *
     * @param purchaseOrderId Purchase order ID
     * @param userPermissions User's permission set
     * @return List of transactions
     */
    List<Transaction> listTransactionsByPurchase(long purchaseOrderId, Set<String> userPermissions);
}

package com.possum.application.transactions;

import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Example usage of TransactionService
 */
public final class TransactionServiceExample {

    public static void main(String[] args) {
        // Assume transactionService is injected via TransactionsModule
        TransactionService transactionService = null;
        Set<String> userPermissions = Set.of("sales.view");

        // Example 1: Get all transactions with pagination
        TransactionFilter filter = new TransactionFilter(
                null,                    // startDate
                null,                    // endDate
                null,                    // type
                null,                    // minAmount
                null,                    // maxAmount
                null,                    // paymentMethodId
                null,                    // status
                null,                    // searchTerm
                1,                       // currentPage
                20,                      // itemsPerPage
                "transaction_date",      // sortBy
                "DESC"                   // sortOrder
        );
        PagedResult<Transaction> transactions = transactionService.getTransactions(filter, userPermissions);
        System.out.println("Total transactions: " + transactions.totalCount());

        // Example 2: Filter by date range
        TransactionFilter dateFilter = new TransactionFilter(
                "2024-01-01",
                "2024-12-31",
                null, null, null, null, null, null,
                1, 20,
                "transaction_date", "DESC"
        );
        PagedResult<Transaction> dateFiltered = transactionService.getTransactions(dateFilter, userPermissions);

        // Example 3: Filter by type
        TransactionFilter typeFilter = new TransactionFilter(
                null, null,
                List.of("payment"),      // type
                null, null, null, null, null,
                1, 20,
                "transaction_date", "DESC"
        );
        PagedResult<Transaction> payments = transactionService.getTransactions(typeFilter, userPermissions);

        // Example 4: Search by invoice/customer/supplier
        TransactionFilter searchFilter = new TransactionFilter(
                null, null, null, null, null, null, null,
                "INV-001",               // searchTerm
                1, 20,
                "transaction_date", "DESC"
        );
        PagedResult<Transaction> searched = transactionService.getTransactions(searchFilter, userPermissions);

        // Example 5: Get transaction by ID
        Optional<Transaction> transaction = transactionService.getTransactionById(1L, userPermissions);
        transaction.ifPresent(t -> {
            System.out.println("Transaction ID: " + t.id());
            System.out.println("Amount: " + t.amount());
            System.out.println("Type: " + t.type());
        });

        // Example 6: Get all transactions for a sale
        List<Transaction> saleTransactions = transactionService.listTransactionsBySale(1L, userPermissions);
        System.out.println("Sale has " + saleTransactions.size() + " transactions");

        // Example 7: Get all transactions for a purchase order
        List<Transaction> purchaseTransactions = transactionService.listTransactionsByPurchase(1L, userPermissions);
        System.out.println("Purchase order has " + purchaseTransactions.size() + " transactions");

        // Example 8: Permission check - will throw AuthorizationException
        try {
            Set<String> noPermissions = Set.of();
            transactionService.getTransactions(filter, noPermissions);
        } catch (Exception e) {
            System.out.println("Access denied: " + e.getMessage());
        }
    }
}

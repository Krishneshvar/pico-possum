package com.possum.application.transactions;

import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.model.Transaction;
import com.possum.domain.repositories.SalesRepository;
import com.possum.domain.repositories.TransactionRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final SalesRepository salesRepository;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            SalesRepository salesRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.salesRepository = salesRepository;
    }

    @Override
    public PagedResult<Transaction> getTransactions(TransactionFilter filter, Set<String> userPermissions) {
        checkPermissions(userPermissions);

        int page = Math.max(1, filter.currentPage());
        int limit = Math.min(100, Math.max(1, filter.itemsPerPage()));

        TransactionFilter sanitized = new TransactionFilter(
                filter.startDate(),
                filter.endDate(),
                filter.type(),
                filter.minAmount(),
                filter.maxAmount(),
                filter.paymentMethodId(),
                filter.status(),
                filter.searchTerm(),
                page,
                limit,
                filter.sortBy(),
                filter.sortOrder()
        );

        return transactionRepository.findTransactions(sanitized);
    }

    @Override
    public Optional<Transaction> getTransactionById(long id, Set<String> userPermissions) {
        checkPermissions(userPermissions);
        return transactionRepository.findTransactionById(id);
    }

    @Override
    public List<Transaction> listTransactionsBySale(long saleId, Set<String> userPermissions) {
        checkPermissions(userPermissions);
        return salesRepository.findTransactionsBySaleId(saleId);
    }

    @Override
    public List<Transaction> listTransactionsByPurchase(long purchaseOrderId, Set<String> userPermissions) {
        checkPermissions(userPermissions);
        return transactionRepository.findTransactionsByPurchaseOrderId(purchaseOrderId);
    }

    private void checkPermissions(Set<String> userPermissions) {
        boolean canView = userPermissions.contains("*")
                || userPermissions.contains("transactions.view")
                || userPermissions.contains("reports.view")
                || userPermissions.contains("sales.view")
                || userPermissions.contains("sales.create");

        if (!canView) {
            throw new AuthorizationException("Forbidden: Missing required permission to view transactions");
        }
    }
}

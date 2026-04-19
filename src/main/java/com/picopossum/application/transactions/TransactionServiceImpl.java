package com.picopossum.application.transactions;

import com.picopossum.domain.model.Transaction;
import com.picopossum.domain.repositories.SalesRepository;
import com.picopossum.domain.repositories.TransactionRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.TransactionFilter;

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
        return transactionRepository.findTransactionById(id);
    }

    @Override
    public List<Transaction> listTransactionsBySale(long saleId, Set<String> userPermissions) {
        return salesRepository.findTransactionsBySaleId(saleId);
    }
}

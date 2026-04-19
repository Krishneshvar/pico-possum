package com.picopossum.domain.repositories;

import com.picopossum.domain.model.Transaction;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.TransactionFilter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    PagedResult<Transaction> findTransactions(TransactionFilter filter);

    Optional<Transaction> findTransactionById(long id);

    long insertTransaction(Transaction transaction, Long saleId);

    BigDecimal getTotalRefundedForSale(long saleId);

    BigDecimal getTotalPaidForSale(long saleId);
}

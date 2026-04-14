package com.possum.domain.repositories;

import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    PagedResult<Transaction> findTransactions(TransactionFilter filter);

    Optional<Transaction> findTransactionById(long id);

    List<Transaction> findTransactionsByPurchaseOrderId(long purchaseOrderId);

    long insertTransaction(Transaction transaction, Long saleId, Long purchaseOrderId);

    BigDecimal getTotalRefundedForSale(long saleId);

    BigDecimal getTotalPaidForSale(long saleId);
}

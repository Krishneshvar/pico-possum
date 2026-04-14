package com.possum.application.transactions;

import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.model.Transaction;
import com.possum.domain.repositories.SalesRepository;
import com.possum.domain.repositories.TransactionRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private SalesRepository salesRepository;

    private TransactionService transactionService;
    private Set<String> adminPermissions = Set.of("*");
    private Set<String> viewPermissions = Set.of("transactions.view");
    private Set<String> noPermissions = Set.of();

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(transactionRepository, salesRepository);
    }

    @Test
    void getTransactions_withPermissions_shouldReturnResults() {
        TransactionFilter filter = new TransactionFilter(null, null, null, null, null, null, null, null, 1, 20, "id", "desc");
        PagedResult<Transaction> expectedResult = new PagedResult<>(List.of(), 0, 0, 1, 20);
        
        when(transactionRepository.findTransactions(any())).thenReturn(expectedResult);

        PagedResult<Transaction> result = transactionService.getTransactions(filter, viewPermissions);

        assertNotNull(result);
        verify(transactionRepository).findTransactions(any());
    }

    @Test
    void getTransactions_withoutPermissions_shouldThrowException() {
        TransactionFilter filter = new TransactionFilter(null, null, null, null, null, null, null, null, 1, 20, "id", "desc");

        assertThrows(AuthorizationException.class, () -> 
            transactionService.getTransactions(filter, noPermissions)
        );
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionById_withPermissions_shouldReturnTransaction() {
        Transaction transaction = createTestTransaction(1L);
        when(transactionRepository.findTransactionById(1L)).thenReturn(Optional.of(transaction));

        Optional<Transaction> result = transactionService.getTransactionById(1L, adminPermissions);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().id());
    }

    @Test
    void listTransactionsBySale_shouldReturnList() {
        List<Transaction> transactions = List.of(createTestTransaction(1L), createTestTransaction(2L));
        when(salesRepository.findTransactionsBySaleId(10L)).thenReturn(transactions);

        List<Transaction> result = transactionService.listTransactionsBySale(10L, viewPermissions);

        assertEquals(2, result.size());
        verify(salesRepository).findTransactionsBySaleId(10L);
    }

    @Test
    void listTransactionsByPurchase_shouldReturnList() {
        List<Transaction> transactions = List.of(createTestTransaction(3L));
        when(transactionRepository.findTransactionsByPurchaseOrderId(20L)).thenReturn(transactions);

        List<Transaction> result = transactionService.listTransactionsByPurchase(20L, viewPermissions);

        assertEquals(1, result.size());
        verify(transactionRepository).findTransactionsByPurchaseOrderId(20L);
    }

    private Transaction createTestTransaction(long id) {
        return new Transaction(
            id, BigDecimal.TEN, "SALE", 100L, "CASH", "COMPLETED",
            LocalDateTime.now(), "Ref" + id, "Customer Name", "Supplier Name"
        );
    }
}

package com.possum.ui.transactions;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.sales.SalesService;
import com.possum.application.transactions.TransactionService;
import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionsControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private TransactionService transactionService;
    @Mock private SalesService salesService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private TransactionsController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("transactions:view")));
        
        // Manual injection of inherited FXML fields which @InjectMocks might miss
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);

        lenient().when(paginationBar.getCurrentPage()).thenReturn(0);
        lenient().when(paginationBar.getPageSize()).thenReturn(25);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = com.possum.ui.common.controllers.AbstractCrudController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch transactions logic")
    void fetchData_success() {
        TransactionFilter filter = new TransactionFilter(null, null, null, null, null, null, null, null, 0, 25, "transaction_date", "DESC");
        List<Transaction> transactions = List.of(
            createTestTransaction(1L, "INV-001", new BigDecimal("100.00"), "payment"),
            createTestTransaction(2L, "INV-002", new BigDecimal("-50.00"), "refund")
        );
        PagedResult<Transaction> pagedResult = new PagedResult<>(transactions, 2, 1, 0, 25);
        
        when(transactionService.getTransactions(any(TransactionFilter.class), anySet())).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<Transaction> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(transactionService).getTransactions(any(TransactionFilter.class), anySet());
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        TransactionFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
        assertEquals("transaction_date", filter.sortBy());
        assertEquals("DESC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should throw exception on delete")
    void deleteEntity_throwsException() {
        Transaction tx = createTestTransaction(1L, "INV-001", BigDecimal.TEN, "payment");
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(tx));
    }

    private Transaction createTestTransaction(Long id, String invoiceNumber, BigDecimal amount, String type) {
        return new Transaction(
            id, amount, type, 1L, "Cash", "completed", LocalDateTime.now(),
            invoiceNumber, "Test Customer", null
        );
    }
}

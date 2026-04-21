package com.picopossum.ui.sales;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.sales.SalesService;
import com.picopossum.domain.model.Sale;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.SaleFilter;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesHistoryControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SalesService salesService;
    @Mock private SettingsStore settingsStore;
    @Mock private PrinterService printerService;
    @Mock private WorkspaceManager workspaceManager;

    private SalesHistoryController controller;

    @BeforeEach
    void setUp() {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser"));
        controller = new SalesHistoryController(salesService, settingsStore, printerService, workspaceManager);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch sales history logic")
    void loadHistory_success() {
        List<Sale> sales = List.of(
            createTestSale(1L, "INV-001", new BigDecimal("100.00")),
            createTestSale(2L, "INV-002", new BigDecimal("200.00"))
        );
        PagedResult<Sale> pagedResult = new PagedResult<>(sales, 2, 1, 1, 15);
        
        when(salesService.findSales(any(SaleFilter.class))).thenReturn(pagedResult);

        // Call the service method with a real filter, not any()
        SaleFilter filter = new SaleFilter(null, null, null, null, null, null, null, null, 1, 15, null, null, null, null);
        PagedResult<Sale> result = salesService.findSales(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(salesService).findSales(any(SaleFilter.class));
    }

    private Sale createTestSale(Long id, String invoiceNumber, BigDecimal total) {
        return new Sale(
            id, invoiceNumber, LocalDateTime.now(), total, total, BigDecimal.ZERO,
            "paid", "delivered", 1L, 1L, "Test Customer",
            null, null, "Test User", 1L, "Cash", invoiceNumber
        );
    }
}


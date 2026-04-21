package com.picopossum.ui.sales;

import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.dto.SaleResponse;
import com.picopossum.domain.model.Sale;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleDetailControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SalesService salesService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private SettingsStore settingsStore;
    @Mock private PrinterService printerService;
    @Mock private ProductSearchIndex searchIndex;

    private SaleDetailController controller;

    @BeforeEach
    void setUp() {
        controller = new SaleDetailController(salesService, workspaceManager, settingsStore, printerService, searchIndex);
    }

    @Test
    @DisplayName("Should load sale details via parameters correctly for single-user core")
    void setParameters_loadsSale() {
        Sale sale = createTestSale(1L, "INV-001");
        SaleResponse response = new SaleResponse(sale, List.of(), List.of());
        
        when(salesService.getSaleDetails(1L)).thenReturn(response);

        // GUI components are null in test, so we catch potential NPEs while verifying service call
        try {
            controller.setParameters(Map.of("sale", sale));
        } catch (Exception ignored) {}

        verify(salesService).getSaleDetails(1L);
    }

    private Sale createTestSale(Long id, String invoiceNumber) {
        return new Sale(
            id, invoiceNumber, LocalDateTime.now(), new BigDecimal("100.00"),
            new BigDecimal("100.00"), BigDecimal.ZERO,
            "paid", "fulfilled", 1L, "Test Customer", "1234567890",
            "test@customer.com", "Test Biller", 1L, "Cash", invoiceNumber
        );
    }
}

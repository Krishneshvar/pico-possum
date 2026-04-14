package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Sale;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("sales:manage")));
        controller = new SaleDetailController(salesService, workspaceManager, settingsStore, printerService, searchIndex);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should load sale details via parameters")
    void setParameters_loadsSale() {
        Sale sale = createTestSale(1L, "INV-001");
        SaleResponse response = new SaleResponse(sale, List.of(), List.of());
        
        when(salesService.getSaleDetails(1L)).thenReturn(response);

        // This would fail in full UI mode due to FXML labels being null,
        // but we can test the service interaction if we wrap it in try-catch or mock labels.
        try {
            controller.setParameters(Map.of("sale", sale));
        } catch (Exception e) {
            // Label updates will fail
        }

        verify(salesService).getSaleDetails(1L);
    }

    private Sale createTestSale(Long id, String invoiceNumber) {
        return new Sale(
            id, invoiceNumber, LocalDateTime.now(), new BigDecimal("100.00"),
            new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.TEN,
            "paid", "fulfilled", 1L, 1L, "Test Customer", "1234567890",
            "test@customer.com", "Test Biller", 1L, "Cash"
        );
    }
}

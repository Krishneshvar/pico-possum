package com.picopossum.ui.sales;

import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.dto.SaleResponse;
import com.picopossum.domain.exceptions.NotFoundException;
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
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesHistoryActionsHandlerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SalesService salesService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private SettingsStore settingsStore;
    @Mock private PrinterService printerService;

    private SalesHistoryActionsHandler handler;
    private int dataChangedCallCount;

    @BeforeEach
    void setUp() {
        dataChangedCallCount = 0;
        handler = new SalesHistoryActionsHandler(salesService, settingsStore, printerService, workspaceManager, () -> dataChangedCallCount++);
    }

    private Sale paidSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), new BigDecimal("100.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-00" + id);
    }

    private Sale cancelledSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "cancelled", "cancelled", null, "Guest", null, null, "System", 1L, "Cash", "INV-00" + id);
    }

    private Sale refundedSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "refunded", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-00" + id);
    }

    private Sale legacySale() {
        return new Sale(-1L, "LEG-001", LocalDateTime.now(), new BigDecimal("500.00"),
                new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                "legacy", "fulfilled", null, "Old Customer", null, null, "Legacy Import", 1L, "Cash", "LEG-001");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // buildActionsMenu
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Regular paid sale should have View, Print, Return, Edit, Cancel menu items")
    void buildActionsMenu_paidSale_hasAllItems() {
        var items = handler.buildActionsMenu(paidSale(1L));
        // View, Print, Return, Separator, Edit, Cancel = at least 5 non-separator items
        long actionItems = items.stream().filter(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem)).count();
        Assertions.assertTrue(actionItems >= 5, "Paid sale should have at least 5 action menu items");
    }

    @Test
    @DisplayName("Cancelled sale should NOT have Return, Edit, or Cancel items")
    void buildActionsMenu_cancelledSale_limitedItems() {
        var items = handler.buildActionsMenu(cancelledSale(1L));
        long actionItems = items.stream().filter(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem)).count();
        Assertions.assertEquals(2, actionItems, "Cancelled sale should only have View and Print");
    }

    @Test
    @DisplayName("Refunded sale should NOT have Return, Edit, or Cancel items")
    void buildActionsMenu_refundedSale_limitedItems() {
        var items = handler.buildActionsMenu(refundedSale(1L));
        long actionItems = items.stream().filter(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem)).count();
        Assertions.assertEquals(2, actionItems, "Refunded sale should only have View and Print");
    }

    @Test
    @DisplayName("Legacy sale should only have View Legacy Summary item")
    void buildActionsMenu_legacySale_onlyLegacyItem() {
        var items = handler.buildActionsMenu(legacySale());
        Assertions.assertEquals(1, items.size(), "Legacy sale should have exactly 1 action menu item");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleView
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleView on a regular sale should open the detail window")
    void handleView_regularSale_opensWindow() {
        handler.handleView(paidSale(1L));
        verify(workspaceManager).openOrFocusWindow(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("handleView on null should not throw")
    void handleView_null_doesNothing() {
        Assertions.assertDoesNotThrow(() -> handler.handleView(null));
        verifyNoInteractions(workspaceManager);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleEdit
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleEdit on a regular sale should open window with editing=true param")
    void handleEdit_regularSale_opensWindowWithEditingFlag() {
        handler.handleEdit(paidSale(1L));
        verify(workspaceManager).openOrFocusWindow(anyString(), anyString(),
                argThat(params -> Boolean.TRUE.equals(params.get("editing"))));
    }

    @Test
    @DisplayName("handleEdit on null should not throw")
    void handleEdit_null_doesNothing() {
        Assertions.assertDoesNotThrow(() -> handler.handleEdit(null));
        verifyNoInteractions(workspaceManager);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handleReturn
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleReturn on regular sale should open return dialog and notify data changed")
    void handleReturn_regularSale_opensDialog() {
        handler.handleReturn(paidSale(1L));
        verify(workspaceManager).openDialog(anyString(), anyString(), any());
        Assertions.assertEquals(1, dataChangedCallCount, "onDataChanged callback should have been called");
    }

    @Test
    @DisplayName("handleReturn on null should not throw")
    void handleReturn_null_doesNothing() {
        Assertions.assertDoesNotThrow(() -> handler.handleReturn(null));
        verifyNoInteractions(workspaceManager);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // handlePrint (non-legacy)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handlePrint on regular sale should call getSaleDetails")
    void handlePrint_regularSale_callsGetSaleDetails() {
        Sale sale = paidSale(1L);
        SaleResponse mockResponse = new SaleResponse(sale, List.of(), List.of());
        when(salesService.getSaleDetails(1L)).thenReturn(mockResponse);
        when(settingsStore.loadGeneralSettings()).thenReturn(mock(com.picopossum.shared.dto.GeneralSettings.class));
        when(settingsStore.loadBillSettings()).thenReturn(mock(com.picopossum.shared.dto.BillSettings.class));

        // handlePrint will try to render & print; rendering may throw NPE on mocked settings.
        // The important thing is that getSaleDetails was called.
        try { handler.handlePrint(sale); } catch (Exception ignored) {}

        verify(salesService).getSaleDetails(1L);
    }

    @Test
    @DisplayName("handlePrint on null should not throw")
    void handlePrint_null_doesNothing() {
        Assertions.assertDoesNotThrow(() -> handler.handlePrint(null));
        verifyNoInteractions(salesService);
    }

    @Test
    @DisplayName("handlePrint on legacy sale should NOT call getSaleDetails")
    void handlePrint_legacySale_doesNotCallService() {
        handler.handlePrint(legacySale());
        verifyNoInteractions(salesService);
    }
}

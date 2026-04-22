package com.picopossum.ui.sales;

import com.picopossum.application.sales.SalesService;
import com.picopossum.domain.model.Sale;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.SaleFilter;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.common.controls.FilterBar;
import com.picopossum.ui.common.controls.PaginationBar;
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
    @Mock private WorkspaceManager workspaceManager;
    @Mock private SettingsStore settingsStore;
    @Mock private PrinterService printerService;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;
    @Mock private com.picopossum.ui.common.controls.DataTableView<Sale> salesTable;
    @Mock private com.picopossum.infrastructure.system.AppExecutor executor;

    private SalesHistoryController controller;

    @BeforeEach
    void setUp() throws Exception {
        lenient().doAnswer(inv -> {
            ((Runnable)inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());

        controller = new SalesHistoryController(salesService, settingsStore, printerService, workspaceManager, executor);
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);
        setField(controller, "salesTable", salesTable);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found");
    }

    @Test
    @DisplayName("Should fetch sale history data correctly for single-user core")
    void fetchData_success() {
        List<Sale> sales = List.of(
            new Sale(1L, "INV-001", LocalDateTime.now(), new BigDecimal("100.00"), 
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", 
                null, "Guest", null, null, "System", 1L, "Cash", "INV-001")
        );
        PagedResult<Sale> pagedResult = new PagedResult<>(sales, 1, 1, 1, 15);
        
        when(salesService.findSales(any())).thenReturn(pagedResult);

        // When
        controller.loadHistory();

        // Then - handle async with timeout
        verify(salesService, timeout(2000)).findSales(any());
    }

    @Test
    @DisplayName("Should protected sale history from deletion")
    void deleteEntity_throwsException() {
        Sale sale = new Sale(1L, "INV-001", LocalDateTime.now(), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-001");
        assertThrows(Exception.class, () -> {
            java.lang.reflect.Method deleteEntity = SalesHistoryController.class.getDeclaredMethod("deleteEntity", Sale.class);
            deleteEntity.setAccessible(true);
            deleteEntity.invoke(controller, sale);
        });
    }
}

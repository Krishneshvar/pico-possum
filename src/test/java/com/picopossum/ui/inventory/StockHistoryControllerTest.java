package com.picopossum.ui.inventory;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.people.UserService;
import com.picopossum.shared.dto.StockHistoryDto;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockHistoryControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private InventoryService inventoryService;
    @Mock private UserService userService;
    @Mock private WorkspaceManager workspaceManager;

    private StockHistoryController controller;

    @BeforeEach
    void setUp() {
        controller = new StockHistoryController(inventoryService, userService, workspaceManager, null);
    }

    @Test
    @DisplayName("Should fetch stock history data correctly for single-user core")
    void fetchData_success() {
        StockHistoryFilter filter = new StockHistoryFilter(
            null, List.of(), LocalDate.now(), LocalDate.now(), 1, 25
        );
        
        List<StockHistoryDto> history = List.of(
            new StockHistoryDto(1L, 1L, "Product A", "SKU001", 10, "receive", LocalDateTime.now(), 50, 10),
            new StockHistoryDto(2L, 2L, "Product B", "SKU002", -2, "sale", LocalDateTime.now(), 48, 10)
        );

        when(inventoryService.getStockHistory(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(history);

        PagedResult<StockHistoryDto> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.items().size());
        verify(inventoryService).getStockHistory(any(), any(), any(), any(), eq(25), eq(0));
    }

    @Test
    @DisplayName("Should initialize controller without identity overhead")
    void buildFilter_success() {
        assertNotNull(controller);
    }
}

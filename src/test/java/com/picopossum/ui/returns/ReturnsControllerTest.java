package com.picopossum.ui.returns;

import com.picopossum.application.returns.ReturnsService;
import com.picopossum.application.sales.SalesService;
import com.picopossum.domain.model.Return;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;
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
class ReturnsControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private ReturnsService returnsService;
    @Mock private SalesService salesService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    private ReturnsController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new ReturnsController(returnsService, salesService, workspaceManager, null);
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);
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
    @DisplayName("Should fetch return data for single-user core")
    void fetchData_success() {
        ReturnFilter filter = new ReturnFilter(null, null, null, null, null, null, null, 0, 25, "created_at", "DESC");
        List<Return> items = List.of(
            new Return(1L, 100L, "Defective", LocalDateTime.now(), "INV-100", "Admin", new BigDecimal("50.00"), 1L, "Cash", "RET-001")
        );
        PagedResult<Return> pagedResult = new PagedResult<>(items, 1, 1, 0, 25);
        
        when(returnsService.getReturns(any())).thenReturn(pagedResult);

        PagedResult<Return> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        verify(returnsService).getReturns(any());
    }

    @Test
    @DisplayName("Should protected sale history from deletion")
    void deleteEntity_throwsException() {
        Return ret = new Return(1L, 100L, "test", LocalDateTime.now(), "INV-100", "Admin", BigDecimal.TEN, 1L, "Cash", "RET-001");
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(ret));
    }
}

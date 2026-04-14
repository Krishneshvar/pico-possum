package com.possum.ui.returns;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.returns.ReturnsService;
import com.possum.application.sales.SalesService;
import com.possum.domain.model.Return;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;
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

    @org.mockito.InjectMocks
    private ReturnsController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("returns:view")));
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
    @DisplayName("Should fetch returns logic")
    void fetchData_success() {
        ReturnFilter filter = new ReturnFilter(null, null, null, null, null, null, null, null, 0, 25, "created_at", "DESC");
        List<Return> returns = List.of(
            createTestReturn(1L, "INV-001", new BigDecimal("50.00")),
            createTestReturn(2L, "INV-002", new BigDecimal("75.00"))
        );
        PagedResult<Return> pagedResult = new PagedResult<>(returns, 2, 1, 0, 25);
        
        when(returnsService.getReturns(any(ReturnFilter.class))).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<Return> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(returnsService).getReturns(any(ReturnFilter.class));
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        ReturnFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
        assertEquals("created_at", filter.sortBy());
        assertEquals("DESC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should throw exception on delete")
    void deleteEntity_throwsException() {
        Return returnRec = createTestReturn(1L, "INV-001", BigDecimal.TEN);
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(returnRec));
    }

    private Return createTestReturn(Long id, String invoiceNumber, BigDecimal totalRefund) {
        return new Return(
            id, 1L, 1L, "Damaged product", LocalDateTime.now(), invoiceNumber,
            "Test Admin", totalRefund, 1L, "Cash"
        );
    }
}

package com.picopossum.ui.audit;

import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.common.controls.FilterBar;
import com.picopossum.ui.common.controls.PaginationBar;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private AuditService auditService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    private AuditController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new AuditController(auditService, workspaceManager);
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);
        lenient().when(paginationBar.getCurrentPage()).thenReturn(0);
        lenient().when(paginationBar.getPageSize()).thenReturn(25);
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
    @DisplayName("Should fetch audit logs logic correctly")
    void fetchData_success() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "created_at", "DESC", 0, 25);
        List<AuditLog> logs = List.of(
            createTestAuditLog(1L, "products", "INSERT", 1L),
            createTestAuditLog(2L, "products", "UPDATE", 1L)
        );
        PagedResult<AuditLog> pagedResult = new PagedResult<>(logs, 2, 1, 0, 25);
        
        when(auditService.listAuditEvents(any(AuditLogFilter.class))).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<AuditLog> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(auditService).listAuditEvents(any(AuditLogFilter.class));
    }

    @Test
    @DisplayName("Should build filter correctly aligned with single-user core")
    void buildFilter_success() {
        AuditLogFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
        assertEquals("created_at", filter.sortBy());
        assertEquals("DESC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should throw exception on delete for audit logs")
    void deleteEntity_throwsException() {
        AuditLog log = createTestAuditLog(1L, "test", "INSERT", 1L);
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(log));
    }

    private AuditLog createTestAuditLog(Long id, String tableName, String action, Long rowId) {
        return new AuditLog(id, action, tableName, rowId, null, null, "Summary Details", LocalDateTime.now());
    }
}

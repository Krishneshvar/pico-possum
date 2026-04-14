package com.possum.ui.audit;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.audit.AuditService;
import com.possum.domain.model.AuditLog;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
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
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("audit:view")));
        controller = new AuditController(auditService, workspaceManager);
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
    @DisplayName("Should fetch audit logs logic")
    void fetchData_success() {
        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, null, "created_at", "DESC", 0, 25);
        List<AuditLog> logs = List.of(
            createTestAuditLog(1L, "products", "INSERT", 1L, "testuser"),
            createTestAuditLog(2L, "products", "UPDATE", 1L, "testuser")
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
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        AuditLogFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
        assertEquals("created_at", filter.sortBy());
        assertEquals("DESC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should throw exception on delete")
    void deleteEntity_throwsException() {
        AuditLog log = createTestAuditLog(1L, "test", "INSERT", 1L, "user");
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(log));
    }

    private AuditLog createTestAuditLog(Long id, String tableName, String action, Long rowId, String userName) {
        return new AuditLog(
            id, 1L, action, tableName, rowId, null, null, "Summary Details", userName, LocalDateTime.now()
        );
    }
}

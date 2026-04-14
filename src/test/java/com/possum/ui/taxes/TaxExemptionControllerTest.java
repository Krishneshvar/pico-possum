package com.possum.ui.taxes;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.people.CustomerService;
import com.possum.application.taxes.TaxExemptionService;
import com.possum.domain.model.TaxExemption;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
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
class TaxExemptionControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private TaxExemptionService taxExemptionService;
    @Mock private CustomerService customerService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private TaxExemptionController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("settings:manage")));
        
        // Manual injection of inherited FXML fields which @InjectMocks might miss
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
    @DisplayName("Should fetch tax exemptions logic")
    void fetchData_success() {
        TaxExemptionController.TaxExemptionFilter filter = new TaxExemptionController.TaxExemptionFilter(null, 1, 25);
        List<TaxExemption> exemptions = List.of(
            createTestExemption(1L, 1L, "Type A", "CERT-001"),
            createTestExemption(2L, 2L, "Type B", "CERT-002")
        );
        
        when(taxExemptionService.getCustomerExemptions(isNull())).thenReturn(exemptions);

        // Call the controller method
        PagedResult<TaxExemption> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(taxExemptionService).getCustomerExemptions(isNull());
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        TaxExemptionController.TaxExemptionFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(1, filter.page()); // buildFilter starts from page 1 in this controller
        assertEquals(25, filter.pageSize());
    }

    @Test
    @DisplayName("Should delete tax exemption successfully")
    void deleteEntity_success() throws Exception {
        TaxExemption exemption = createTestExemption(1L, 1L, "Type A", "CERT-001");
        doNothing().when(taxExemptionService).deleteExemption(eq(1L), anyLong());

        controller.deleteEntity(exemption);

        verify(taxExemptionService).deleteExemption(eq(1L), anyLong());
    }

    private TaxExemption createTestExemption(Long id, Long customerId, String type, String cert) {
        return new TaxExemption(
            id, customerId, type, cert, "Reason", LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 1L, LocalDateTime.now(), LocalDateTime.now()
        );
    }
}

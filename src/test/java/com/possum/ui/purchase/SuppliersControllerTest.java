package com.possum.ui.purchase;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.domain.model.Supplier;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SupplierFilter;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SuppliersControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SupplierRepository supplierRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private SuppliersController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("suppliers:view")));
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
    @DisplayName("Should fetch suppliers logic")
    void fetchData_success() {
        SupplierFilter filter = new SupplierFilter(0, 25, null, null, "name", "ASC");
        List<Supplier> suppliers = List.of(
            createTestSupplier(1L, "Supplier A", "1234567890"),
            createTestSupplier(2L, "Supplier B", "0987654321")
        );
        PagedResult<Supplier> pagedResult = new PagedResult<>(suppliers, 2, 1, 0, 25);
        
        when(supplierRepository.getAllSuppliers(any(SupplierFilter.class))).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<Supplier> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(supplierRepository).getAllSuppliers(any(SupplierFilter.class));
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        SupplierFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.page());
        assertEquals(25, filter.limit());
        assertEquals("name", filter.sortBy());
        assertEquals("ASC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should delete supplier successfully")
    void deleteEntity_success() throws Exception {
        Supplier supplier = createTestSupplier(1L, "Test Supplier", "123");
        when(supplierRepository.deleteSupplier(1L)).thenReturn(1);

        controller.deleteEntity(supplier);

        verify(supplierRepository).deleteSupplier(1L);
    }

    private Supplier createTestSupplier(Long id, String name, String phone) {
        return new Supplier(
            id, name, "Contact", phone, "email@test.com", "Address", "GSTIN123",
            1L, "Policy A", LocalDateTime.now(), LocalDateTime.now(), null
        );
    }
}

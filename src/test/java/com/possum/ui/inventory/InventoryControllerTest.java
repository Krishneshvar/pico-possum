package com.possum.ui.inventory;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.categories.CategoryService;
import com.possum.domain.model.Variant;
import com.possum.domain.repositories.VariantRepository;
import com.possum.domain.repositories.TaxRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private InventoryService inventoryService;
    @Mock private VariantRepository variantRepository;
    @Mock private CategoryService categoryService;
    @Mock private TaxRepository taxRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private InventoryController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("inventory:view")));
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
    @DisplayName("Should fetch inventory data")
    void fetchData_success() {
        InventoryFilter filter = new InventoryFilter(null, null, null, null, null, null, null, 0, 25);
        List<Variant> variants = List.of(
            createTestVariant(1L, "Product 1", 10),
            createTestVariant(2L, "Product 2", 5)
        );
        PagedResult<Variant> pagedResult = new PagedResult<>(variants, 2, 1, 0, 25);
        
        when(variantRepository.findVariants(any(), any(), any(), any(), any(), any(), any(), any(), anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(pagedResult);

        // Call controller method
        PagedResult<Variant> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(variantRepository).findVariants(any(), any(), any(), any(), any(), any(), any(), any(), eq("stock"), eq("ASC"), eq(0), eq(25));
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        // Since we can't easily trigger JavaFX UI changes (filterBar), we test the default filter build
        InventoryFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.page());
        assertEquals(25, filter.limit());
    }

    @Test
    @DisplayName("Should get entity names")
    void getNames_success() {
        assertEquals("inventory", controller.getEntityName());
        assertEquals("Inventory Item", controller.getEntityNameSingular());
    }

    @Test
    @DisplayName("Should throw exception on delete")
    void deleteEntity_throwsException() {
        Variant v = createTestVariant(1L, "Test", 10);
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(v));
    }

    private Variant createTestVariant(Long id, String name, int stock) {
        return new Variant(
            id, 1L, name, "Standard", "SKU" + id, new BigDecimal("10.00"),
            BigDecimal.ZERO, 10, true, "active", null, stock, "Electronics", null, null, null, null
        );
    }
}

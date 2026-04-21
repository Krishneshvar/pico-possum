package com.picopossum.ui.inventory;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.domain.model.Product;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.ui.common.controls.FilterBar;
import com.picopossum.ui.common.controls.PaginationBar;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private InventoryService inventoryService;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryService categoryService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    @org.mockito.InjectMocks
    private InventoryController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser"));
        setField(controller, "paginationBar", paginationBar);
        setField(controller, "filterBar", filterBar);
        lenient().when(paginationBar.getCurrentPage()).thenReturn(0);
        lenient().when(paginationBar.getPageSize()).thenReturn(25);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = com.picopossum.ui.common.controllers.AbstractCrudController.class.getDeclaredField(fieldName);
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
        ProductFilter filter = new ProductFilter(null, null, null, null, null, null, 0, 25, "stock", "ASC");
        List<Product> products = List.of(
            createTestProduct(1L, "Product 1", 10),
            createTestProduct(2L, "Product 2", 5)
        );
        PagedResult<Product> pagedResult = new PagedResult<>(products, 2, 1, 0, 25);

        when(productRepository.findProducts(any())).thenReturn(pagedResult);

        PagedResult<Product> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        verify(productRepository).findProducts(filter);
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        ProductFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
    }

    @Test
    @DisplayName("Should throw exception on delete")
    void deleteEntity_throwsException() {
        Product p = createTestProduct(1L, "Test", 10);
        assertThrows(UnsupportedOperationException.class, () -> controller.deleteEntity(p));
    }

    private Product createTestProduct(Long id, String name, int stock) {
        return new Product(
            id, name, null, null, null, BigDecimal.ZERO, "SKU" + id, null,
            new BigDecimal("10.00"), new BigDecimal("12.00"), 5, "active", null, stock, null, null, null
        );
    }
}


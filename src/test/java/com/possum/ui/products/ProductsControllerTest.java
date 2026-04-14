package com.possum.ui.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.categories.CategoryService;
import com.possum.application.products.ProductService;
import com.possum.domain.model.Category;
import com.possum.domain.model.Product;
import com.possum.domain.repositories.TaxRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProductsControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private ProductService productService;
    @Mock private CategoryService categoryService;
    @Mock private TaxRepository taxRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;
 
    @org.mockito.InjectMocks
    private ProductsController controller;
 
    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("products:view")));
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
    @DisplayName("Should fetch products with filters")
    void fetchProducts_withFilters_success() {
        ProductFilter filter = new ProductFilter(
            "Test", null, List.of("active"), null, 0, 20, "name", "ASC"
        );
        List<Product> products = List.of(
            createTestProduct(1L, "Test Product 1", "active"),
            createTestProduct(2L, "Test Product 2", "active")
        );
        PagedResult<Product> pagedResult = new PagedResult<>(products, 2, 1, 0, 20);
        when(productService.getProducts(any(ProductFilter.class))).thenReturn(pagedResult);

        // Call the controller method
        PagedResult<Product> result = controller.fetchData(filter);

        assertNotNull(result);
        assertEquals(2, result.totalCount());
        assertEquals(2, result.items().size());
        verify(productService).getProducts(any(ProductFilter.class));
    }

    @Test
    @DisplayName("Should delete product successfully")
    void deleteProduct_success() throws Exception {
        Long productId = 1L;
        Long userId = 1L;
        Product product = createTestProduct(productId, "Test Product", "active");
        doNothing().when(productService).deleteProduct(productId, userId);

        // Call the controller method
        controller.deleteEntity(product);

        verify(productService).deleteProduct(productId, userId);
    }

    @Test
    @DisplayName("Should build filter correctly")
    void buildFilter_success() {
        // Set search text in the controller (mocking getSearchOrNull if necessary, 
        // but it's easier to just call the method and check default behavior)
        ProductFilter filter = controller.buildFilter();

        assertNotNull(filter);
        assertEquals(0, filter.currentPage());
        assertEquals(25, filter.itemsPerPage());
        assertEquals("name", filter.sortBy());
        assertEquals("ASC", filter.sortOrder());
    }

    @Test
    @DisplayName("Should get entity name and singular name")
    void getName_success() {
        assertEquals("products", controller.getEntityName());
        assertEquals("Product", controller.getEntityNameSingular());
    }

    private Product createTestProduct(Long id, String name, String status) {
        return new Product(
            id, name, "Description", 1L, "Electronics", 1L, "Standard",
            status, null, 10, LocalDateTime.now(), LocalDateTime.now(), null
        );
    }
}

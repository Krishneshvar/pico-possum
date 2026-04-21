package com.picopossum.ui.products;

import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.products.ProductService;
import com.picopossum.domain.model.Product;
import com.picopossum.ui.JavaFXInitializer;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.common.controls.DataTableView;
import com.picopossum.ui.common.controls.FilterBar;
import com.picopossum.ui.common.controls.PaginationBar;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductsControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private ProductService productService;
    @Mock private CategoryService categoryService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private DataTableView<Product> dataTable;
    @Mock private FilterBar filterBar;
    @Mock private PaginationBar paginationBar;

    private ProductsController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new ProductsController(productService, categoryService, workspaceManager);
        setField(controller, "dataTable", dataTable);
        setField(controller, "filterBar", filterBar);
        setField(controller, "paginationBar", paginationBar);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        // Search in hierarchy for AbstractCrudController fields
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
    @DisplayName("Should successfully delete product in identity-agnostic standard")
    void deleteProduct_success() throws Exception {
        long productId = 123L;
        Product product = new Product(productId, "Test", null, null, null, "SKU", 
                BigDecimal.ONE, BigDecimal.ONE, 10, "active", null, 0, 
                LocalDateTime.now(), LocalDateTime.now(), null);

        doNothing().when(productService).deleteProduct(productId);

        controller.deleteEntity(product);

        verify(productService).deleteProduct(productId);
    }
}

package com.possum.ui.categories;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.categories.CategoryService;
import com.possum.domain.model.Category;
import com.possum.ui.JavaFXInitializer;
import com.possum.ui.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javafx.scene.control.TreeView;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import com.possum.ui.common.controls.DataTableView;
import javafx.scene.control.TableView;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriesControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private CategoryService categoryService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private TreeView<String> categoryTreeView;
    @Mock private TextField searchField;
    @Mock private Button addButton;
    @Mock private Button refreshButton;
    @Mock private DataTableView<Category> dataTable;
    @Mock private TableView<Category> tableView;

    private CategoriesController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("categories:view", "categories:manage")));
        controller = new CategoriesController(categoryService, workspaceManager);
        
        lenient().when(dataTable.getTableView()).thenReturn(tableView);
        lenient().when(tableView.getColumns()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        lenient().when(tableView.comparatorProperty()).thenReturn(new javafx.beans.property.SimpleObjectProperty<>());

        setField(controller, "categoryTreeView", categoryTreeView);
        setField(controller, "searchField", searchField);
        setField(controller, "addButton", addButton);
        setField(controller, "refreshButton", refreshButton);
        setField(controller, "dataTable", dataTable);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        // Try current class first
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            // Try superclass
            java.lang.reflect.Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should fetch categories logic")
    void loadData_success() {
        List<Category> categories = List.of(
            new Category(1L, "Electronics", null, LocalDateTime.now(), LocalDateTime.now(), null),
            new Category(2L, "Laptops", 1L, LocalDateTime.now(), LocalDateTime.now(), null)
        );
        lenient().when(categoryService.getAllCategories()).thenReturn(categories);
        lenient().when(categoryService.getCategoriesAsTree()).thenReturn(List.of());

        // This triggers loadData internally
        controller.loadData();

        verify(categoryService, atLeastOnce()).getAllCategories();
        verify(categoryTreeView).setRoot(any());
    }

    @Test
    @DisplayName("Should get entity names")
    void getNames_success() {
        assertEquals("categories", controller.getEntityName());
        assertEquals("Category", controller.getEntityNameSingular());
    }
}

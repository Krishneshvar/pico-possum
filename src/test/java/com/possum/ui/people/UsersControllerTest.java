package com.possum.ui.people;

import com.possum.application.people.UserService;
import com.possum.domain.model.User;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersControllerTest {

    @Mock private UserService userService;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private FilterBar filterBar;
    @Mock private com.possum.ui.common.controls.PaginationBar paginationBar;
    @Mock private DataTableView<User> dataTable;
    @Mock private TableView<User> tableView;
    @Mock private Button addButton;

    private UsersController controller;

    @BeforeAll
    static void initJavaFX() {
        if (!Platform.isFxApplicationThread()) {
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException e) {
                // Already started
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        controller = new UsersController(userService, workspaceManager);

        // Inject mocks into controller using reflection
        injectField("filterBar", filterBar);
        injectField("paginationBar", paginationBar);
        injectField("dataTable", dataTable);
        injectField("addButton", addButton);

        lenient().when(dataTable.getTableView()).thenReturn(tableView);
        lenient().when(tableView.getColumns()).thenReturn(javafx.collections.FXCollections.observableArrayList());

        lenient().when(userService.getUsers(any())).thenReturn(
            new PagedResult<>(List.of(), 0, 0, 1, 20)
        );
    }

    private void injectField(String name, Object value) throws Exception {
        Field field;
        try {
            field = UsersController.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            field = com.possum.ui.common.controllers.AbstractCrudController.class.getDeclaredField(name);
        }
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    void initialize_shouldSetupUI() {
        controller.initialize();

        verify(filterBar).addMultiSelectFilter(eq("status"), anyString(), anyList(), any(), anyBoolean());
        verify(userService, timeout(1000)).getUsers(any());
    }

    @Test
    void handleAdd_shouldOpenDialog() throws Exception {
        java.lang.reflect.Method method = UsersController.class.getDeclaredMethod("handleAdd");
        method.setAccessible(true);
        method.invoke(controller);
        
        verify(workspaceManager).openDialog(eq("Add Employee"), anyString());
    }

    @Test
    void deleteEntity_shouldInvokeService() throws Exception {
        User user = new User(1L, "test", "test", "pass", true, LocalDateTime.now(), LocalDateTime.now(), null);
        controller.deleteEntity(user);
        verify(userService).deleteUser(1L);
    }
}

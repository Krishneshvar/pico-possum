package com.possum.ui.people;

import com.possum.application.people.UserService;
import com.possum.domain.model.User;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UsersController extends AbstractCrudController<User, UserFilter> {
    
    @FXML private javafx.scene.control.Button addButton;
    
    private final UserService userService;
    private List<Boolean> currentActiveStatuses = null;

    public UsersController(UserService userService, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.userService = userService;
    }

    @Override
    protected void setupPermissions() {
        if (addButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(addButton, com.possum.application.auth.Permissions.USERS_MANAGE);
        }
    }

    @Override
    protected void setupTable() {
        TableColumn<User, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<User, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().username()));
        
        TableColumn<User, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().active() ? "Active" : "Inactive"));
        statusCol.setSortable(false);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createUserStatusBadge(
                        "Active".equalsIgnoreCase(status)));
                    setText(null);
                }
            }
        });
        
        TableColumn<User, java.time.LocalDateTime> createdCol = new TableColumn<>("Created At");
        createdCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().createdAt()));
        createdCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(java.time.LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    java.time.LocalDateTime localTime = com.possum.shared.util.TimeUtil.toLocal(item);
                    setText(localTime != null ? com.possum.shared.util.TimeUtil.formatStandard(localTime) : "");
                }
            }
        });
        
        dataTable.getTableView().getColumns().addAll(nameCol, usernameCol, statusCol, createdCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        filterBar.addMultiSelectFilter("status", "All Statuses", 
            List.of("Active", "Inactive"),
            s -> s,
            false
        );

        setupStandardFilterListener((filters, reload) -> {
            List<String> statuses = (List<String>) filters.get("status");
            if (statuses == null || statuses.isEmpty()) {
                currentActiveStatuses = null;
            } else {
                currentActiveStatuses = statuses.stream().map("Active"::equals).toList();
            }
            reload.run();
        });
    }

    @Override
    protected UserFilter buildFilter() {
        return new UserFilter(
            getSearchOrNull(),
            getCurrentPage(),
            getPageSize(),
            currentActiveStatuses,
            null
        );
    }

    @Override
    protected PagedResult<User> fetchData(UserFilter filter) {
        return userService.getUsers(filter);
    }

    @Override
    protected String getEntityName() {
        return "users";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Employee";
    }

    @Override
    protected List<MenuItem> buildActionMenu(User user) {
        if (!com.possum.ui.common.UIPermissionUtil.hasPermission(com.possum.application.auth.Permissions.USERS_MANAGE)) {
            return List.of();
        }

        return com.possum.ui.common.components.MenuBuilder.create()
            .addEditAction("Edit", () -> workspaceManager.openDialog(
                "Edit Employee: " + user.name(), 
                "/fxml/people/user-form-view.fxml", 
                Map.of("userId", user.id(), "mode", "edit")))
            .addItem("bx-shield", "Roles & Permissions", () -> workspaceManager.openWindow(
                "Roles & Permissions: " + user.name(), 
                "/fxml/people/user-roles-view.fxml", 
                Map.of("userId", user.id())))
            .addSeparator()
            .addDeleteAction("Delete", () -> handleDelete(user))
            .build();
    }

    @Override
    protected void deleteEntity(User entity) throws Exception {
        userService.deleteUser(entity.id());
    }

    @Override
    protected String getEntityIdentifier(User entity) {
        return entity.name();
    }

    @FXML
    private void handleAdd() {
        workspaceManager.openDialog("Add Employee", "/fxml/people/user-form-view.fxml");
    }
}

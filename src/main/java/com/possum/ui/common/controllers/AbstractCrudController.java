package com.possum.ui.common.controllers;

import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import com.possum.ui.common.dialogs.DialogStyler;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Abstract base controller for CRUD list views with table, filters, and pagination.
 * Eliminates duplicate code across Products, Customers, Suppliers, Users, etc.
 * 
 * @param <T> Entity type (Product, Customer, etc.)
 * @param <F> Filter type (ProductFilter, CustomerFilter, etc.)
 */
public abstract class AbstractCrudController<T, F> {

    @FXML protected FilterBar filterBar;
    @FXML protected DataTableView<T> dataTable;
    @FXML protected PaginationBar paginationBar;

    protected final WorkspaceManager workspaceManager;
    protected String currentSearch = "";

    protected AbstractCrudController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @FXML
    public void initialize() {
        setupPermissions();
        setupTable();
        setupFilters();
        loadData();
    }

    /**
     * Setup permission-based UI visibility (buttons, etc.)
     */
    protected abstract void setupPermissions();

    /**
     * Configure table columns
     */
    protected abstract void setupTable();

    /**
     * Configure filter bar with appropriate filters
     */
    protected abstract void setupFilters();

    /**
     * Build filter object from current filter state
     */
    protected abstract F buildFilter();

    /**
     * Fetch data from service/repository
     */
    protected abstract PagedResult<T> fetchData(F filter);

    /**
     * Get entity display name for messages
     */
    protected abstract String getEntityName();

    /**
     * Get entity display name (singular)
     */
    protected abstract String getEntityNameSingular();

    /**
     * Build action menu items for a row
     */
    protected abstract List<MenuItem> buildActionMenu(T entity);

    /**
     * Delete entity
     */
    protected abstract void deleteEntity(T entity) throws Exception;

    /**
     * Get entity identifier for display
     */
    protected abstract String getEntityIdentifier(T entity);

    /**
     * Load data with current filters and pagination
     */
    protected void loadData() {
        dataTable.setLoading(true);
        
        Platform.runLater(() -> {
            try {
                F filter = buildFilter();
                PagedResult<T> result = fetchData(filter);
                
                dataTable.setItems(FXCollections.observableArrayList(result.items()));
                paginationBar.setTotalItems(result.totalCount());
                dataTable.setLoading(false);
            } catch (Exception e) {
                dataTable.setLoading(false);
                com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load " + getEntityName(), e);
                NotificationService.error("Failed to load " + getEntityName() + ": " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
            }
        });
    }

    /**
     * Setup standard filter change listener
     */
    protected void setupStandardFilterListener(BiConsumer<Map<String, Object>, Runnable> customHandler) {
        filterBar.setOnFilterChange(filters -> {
            currentSearch = (String) filters.get("search");
            if (customHandler != null) {
                customHandler.accept(filters, this::loadData);
            } else {
                loadData();
            }
        });
        
        paginationBar.setOnPageChange((page, size) -> loadData());
    }

    /**
     * Setup standard filter listener without custom handling
     */
    protected void setupStandardFilterListener() {
        setupStandardFilterListener(null);
    }

    /**
     * Add action menu column to table
     */
    protected void addActionMenuColumn() {
        dataTable.addMenuActionColumn("Actions", this::buildActionMenu);
    }

    /**
     * Handle refresh action
     */
    @FXML
    protected void handleRefresh() {
        loadData();
        NotificationService.success(getEntityName() + " refreshed");
    }

    /**
     * Handle delete with confirmation
     */
    protected void handleDelete(T entity) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(confirm);
        confirm.setTitle("Delete " + getEntityNameSingular());
        confirm.setHeaderText("Delete " + getEntityIdentifier(entity) + "?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    deleteEntity(entity);
                    NotificationService.success(getEntityNameSingular() + " deleted successfully");
                    loadData();
                } catch (Exception e) {
                    com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to delete " + getEntityNameSingular(), e);
                    NotificationService.error("Failed to delete " + getEntityNameSingular() + ": " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
                }
            }
        });
    }

    /**
     * Get current page number
     */
    protected int getCurrentPage() {
        return paginationBar.getCurrentPage();
    }

    /**
     * Get current page size
     */
    protected int getPageSize() {
        return paginationBar.getPageSize();
    }

    /**
     * Check if search is empty
     */
    protected boolean hasSearch() {
        return currentSearch != null && !currentSearch.isEmpty();
    }

    /**
     * Get search text or null
     */
    protected String getSearchOrNull() {
        return hasSearch() ? currentSearch : null;
    }
}

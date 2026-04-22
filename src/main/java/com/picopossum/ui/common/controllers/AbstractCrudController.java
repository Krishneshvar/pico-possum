package com.picopossum.ui.common.controllers;

import com.picopossum.shared.dto.PagedResult;
import com.picopossum.ui.common.controls.DataTableView;
import com.picopossum.ui.common.controls.FilterBar;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.ui.common.controls.PaginationBar;
import com.picopossum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import com.picopossum.ui.common.dialogs.DialogStyler;
import com.picopossum.infrastructure.system.AppExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCrudController.class);
 
    protected final WorkspaceManager workspaceManager;
    protected final AppExecutor executor;
    protected String currentSearch = "";
 
    protected AbstractCrudController(WorkspaceManager workspaceManager, AppExecutor executor) {
        this.workspaceManager = workspaceManager;
        this.executor = executor;
    }

    @FXML
    public void initialize() {
        initUIComponents();
        setupTable();
        setupFilters();
        loadData();
    }

    /**
     * Setup UI components (buttons, headers, etc.)
     */
    protected abstract void initUIComponents();

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
     * Load data with current filters and pagination.
     * Uses a background thread for fetching to prevent UI freezing.
     */
    protected void loadData() {
        if (dataTable != null) dataTable.setLoading(true);
        
        // Build filter on UI thread since it accesses UI properties
        final F filter = buildFilter();
        
        // Execute fetch using managed executor
        executor.execute(() -> {
            try {
                final PagedResult<T> result = fetchData(filter);
                
                // Update UI on FX Application Thread
                Platform.runLater(() -> {
                    if (dataTable != null) {
                        dataTable.setItems(FXCollections.observableArrayList(result.items()));
                        dataTable.setLoading(false);
                    }
                    if (paginationBar != null) {
                        paginationBar.setTotalItems(result.totalCount());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (dataTable != null) dataTable.setLoading(false);
                    LOGGER.error("Failed to load " + getEntityName(), e);
                    NotificationService.error("Failed to load " + getEntityName() + ": " + 
                        com.picopossum.ui.common.ErrorHandler.toUserMessage(e));
                });
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
                    com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to delete " + getEntityNameSingular(), e);
                    NotificationService.error("Failed to delete " + getEntityNameSingular() + ": " + com.picopossum.ui.common.ErrorHandler.toUserMessage(e));
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

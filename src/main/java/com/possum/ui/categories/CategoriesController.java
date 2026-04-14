package com.possum.ui.categories;

import com.possum.application.categories.CategoryService;
import com.possum.application.categories.CategoryService.CategoryTreeNode;
import com.possum.domain.model.Category;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.controllers.AbstractImportController;
import com.possum.ui.common.components.ButtonFactory;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.CsvImportUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import com.possum.shared.dto.PagedResult;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CategoriesController extends AbstractCrudController<Category, Void> {

    @FXML private TreeView<String> categoryTreeView;
    @FXML private javafx.scene.control.TextField searchField;
    @FXML private javafx.scene.control.Button addButton;
    @FXML private javafx.scene.control.Button refreshButton;
    
    private TableColumn<Category, String> idCol;
    private TableColumn<Category, String> nameCol;
    private TableColumn<Category, String> parentCol;
    private TableColumn<Category, Category> actionsCol;
    
    private final ObservableList<Category> masterData = FXCollections.observableArrayList();
    private FilteredList<Category> filteredData;
    private SortedList<Category> sortedData;
    private final Map<Long, String> categoryNameMap = new HashMap<>();

    private final CategoryService categoryService;
    private final ImportHandler importHandler;

    public CategoriesController(CategoryService categoryService, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.categoryService = categoryService;
        this.importHandler = new ImportHandler();
    }

    @Override
    public void initialize() {
        setupPermissions();
        setupTable();
        setupTreeView();
        setupLocalSearch();
        loadData();
    }

    @Override
    protected void setupPermissions() {
        if (addButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(addButton, com.possum.application.auth.Permissions.CATEGORIES_MANAGE);
            ButtonFactory.applyAddButtonStyle(addButton);
        }
        
        if (refreshButton != null) {
            ButtonFactory.applyRefreshButtonStyle(refreshButton);
        }

        if (importHandler.importButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(importHandler.importButton, com.possum.application.auth.Permissions.CATEGORIES_MANAGE);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No categories found");
        dataTable.setEmptySubtitle("Add your first category to get started.");
        
        idCol = new TableColumn<>("ID");
        nameCol = new TableColumn<>("Name");
        parentCol = new TableColumn<>("Parent Category");
        actionsCol = new TableColumn<>("Actions");

        dataTable.getTableView().getColumns().setAll(idCol, nameCol, parentCol, actionsCol);
        
        idCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().id())));
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        parentCol.setCellValueFactory(cellData -> {
            Long parentId = cellData.getValue().parentId();
            if (parentId == null) {
                return new SimpleStringProperty("-");
            }
            String parentName = categoryNameMap.get(parentId);
            return new SimpleStringProperty(parentName != null ? parentName : String.valueOf(parentId));
        });

        actionsCol.setSortable(false);
        setupActionsColumn();

        filteredData = new FilteredList<>(masterData, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(dataTable.getTableView().comparatorProperty());
        dataTable.getTableView().setItems(sortedData);
    }

    @Override
    protected void setupFilters() {
        // Categories use local search instead of filter bar
    }

    private void setupLocalSearch() {
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(category -> {
                    if (newValue == null || newValue.isEmpty()) {
                        return true;
                    }
                    String lowerCaseFilter = newValue.toLowerCase();
                    if (category.name().toLowerCase().contains(lowerCaseFilter)) {
                        return true;
                    } else if (String.valueOf(category.id()).contains(lowerCaseFilter)) {
                        return true;
                    }
                    return false;
                });
            });
        }
    }

    private void setupTreeView() {
        // Tree view setup handled in loadData
    }

    private void setupActionsColumn() {
        actionsCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue()));
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = ButtonFactory.createEditButton("Edit", () -> {
                Category category = getItem();
                if (category != null) {
                    handleEdit(category);
                }
            });

            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(editBtn);
                }
            }
        });
    }

    @Override
    protected Void buildFilter() {
        return null; // Categories don't use standard filtering
    }

    @Override
    protected PagedResult<Category> fetchData(Void filter) {
        // Not used - categories load all data
        return null;
    }

    @Override
    protected void loadData() {
        List<Category> allCategories = categoryService.getAllCategories();

        categoryNameMap.clear();
        for (Category c : allCategories) {
            categoryNameMap.put(c.id(), c.name());
        }

        masterData.setAll(allCategories);

        // Update tree view
        List<CategoryTreeNode> treeNodes = categoryService.getCategoriesAsTree();
        TreeItem<String> rootItem = new TreeItem<>("All Categories");
        FontIcon rootIcon = new FontIcon("bx-package");
        rootIcon.setIconSize(16);
        rootIcon.setIconColor(javafx.scene.paint.Color.web("#64748b"));
        rootItem.setGraphic(rootIcon);
        
        rootItem.setExpanded(true);
        for (CategoryTreeNode node : treeNodes) {
            rootItem.getChildren().add(buildTreeItem(node));
        }
        categoryTreeView.setRoot(rootItem);
        categoryTreeView.setShowRoot(false);
    }

    private TreeItem<String> buildTreeItem(CategoryTreeNode node) {
        TreeItem<String> item = new TreeItem<>(node.category().name());
        
        FontIcon folderIcon = new FontIcon(node.subcategories().isEmpty() ? "bx-layer" : "bx-folder");
        folderIcon.setIconSize(14);
        folderIcon.setIconColor(javafx.scene.paint.Color.web("#10b981"));
        item.setGraphic(folderIcon);

        item.setExpanded(true);
        for (CategoryTreeNode childNode : node.subcategories()) {
            item.getChildren().add(buildTreeItem(childNode));
        }
        return item;
    }

    @Override
    protected String getEntityName() {
        return "categories";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Category";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Category entity) {
        return List.of(); // Actions handled by column button
    }

    @Override
    protected void deleteEntity(Category entity) throws Exception {
        // Not implemented - categories don't have delete
    }

    @Override
    protected String getEntityIdentifier(Category entity) {
        return entity.name();
    }

    @FXML
    private void handleAddCategory() {
        workspaceManager.showDialog("Add Category", "/fxml/categories/add-category-dialog.fxml", null);
        loadData();
    }

    @FXML
    private void handleImport() {
        importHandler.handleImport();
    }

    private void handleEdit(Category category) {
        Map<String, Object> params = new HashMap<>();
        params.put("category", category);
        workspaceManager.showDialog("Edit Category", "/fxml/categories/add-category-dialog.fxml", params);
        loadData();
    }

    @FXML
    private void handleEditCategory() {
        Category selected = dataTable.getTableView().getSelectionModel().getSelectedItem();
        if (selected != null) {
            handleEdit(selected);
        }
    }

    /**
     * Inner class to handle CSV import functionality
     */
    private class ImportHandler extends AbstractImportController<Category, String> {

        @Override
        protected String[] getRequiredHeaders() {
            return new String[]{"Division Name", "Name"};
        }

        @Override
        protected String parseRow(List<String> row, Map<String, Integer> headers) {
            String name = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(
                row, headers, "Division Name", "Category Name", "Name"
            ));
            return name;
        }

        @Override
        protected Category createEntity(String name) throws Exception {
            List<Category> allCategories = categoryService.getAllCategories();
            Map<String, Long> categoryMap = new HashMap<>();
            for (Category c : allCategories) {
                categoryMap.put(c.name().trim().toLowerCase(Locale.ROOT), c.id());
            }

            String categoryKey = name.toLowerCase(Locale.ROOT);
            if (categoryMap.containsKey(categoryKey)) {
                throw new IllegalArgumentException("Category already exists");
            }

            return categoryService.createCategory(name, null);
        }

        @Override
        protected String getImportTitle() {
            return "Import Categories from CSV";
        }

        @Override
        protected String getEntityName() {
            return "category(ies)";
        }

        @Override
        protected void onImportComplete() {
            loadData();
        }
    }
}

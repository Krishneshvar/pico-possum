package com.possum.ui.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.categories.CategoryService;
import com.possum.application.products.ProductService;
import com.possum.domain.model.Category;
import com.possum.domain.model.Product;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ProductFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.controllers.AbstractImportController;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.CsvImportUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductsController extends AbstractCrudController<Product, ProductFilter> {

    @FXML private Button addButton;
    @FXML private Button refreshButton;

    private final ProductService productService;
    private final CategoryService categoryService;
    private final com.possum.domain.repositories.TaxRepository taxRepository;
    private final ImportHandler importHandler;

    private List<Long> currentTaxCategoryFilters = java.util.Collections.emptyList();
    private List<String> currentStatusFilters = java.util.Collections.emptyList();
    private List<Long> currentCategoryFilters = java.util.Collections.emptyList();

    public ProductsController(ProductService productService,
                              CategoryService categoryService,
                              com.possum.domain.repositories.TaxRepository taxRepository,
                              WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.productService = productService;
        this.categoryService = categoryService;
        this.taxRepository = taxRepository;
        this.importHandler = new ImportHandler();
    }

    @Override
    public void initialize() {
        setupPermissions();
        setupTable();
        setupFilters();
        loadData();
    }

    @Override
    protected void setupPermissions() {
        if (addButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(addButton, com.possum.application.auth.Permissions.PRODUCTS_MANAGE);
            FontIcon addIcon = new FontIcon("bx-plus");
            addIcon.setIconSize(16);
            addIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            addButton.setGraphic(addIcon);
        }

        if (importHandler.importButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(importHandler.importButton, com.possum.application.auth.Permissions.PRODUCTS_MANAGE);
        }

        if (refreshButton != null) {
            FontIcon refreshIcon = new FontIcon("bx-sync");
            refreshIcon.setIconSize(16);
            refreshButton.setGraphic(refreshIcon);
            refreshButton.setText("Refresh");
        }
    }

    @Override
    protected void setupTable() {
        dataTable.getTableView().setPlaceholder(new Label("No products found. Adjust filters or click + Add Product to create one."));
        dataTable.setEmptyMessage("No products found");
        dataTable.setEmptySubtitle("Try changing filters or create your first product.");

        TableColumn<Product, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));

        TableColumn<Product, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().categoryName()));

        TableColumn<Product, String> taxCol = new TableColumn<>("Tax Category");
        taxCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().taxCategoryName() != null ? cellData.getValue().taxCategoryName() : "-"));

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setSortable(false);
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createProductStatusBadge(status));
                    setText(null);
                }
            }
        });

        dataTable.getTableView().getColumns().addAll(nameCol, categoryCol, taxCol, statusCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        List<Category> categories = categoryService.getAllCategories();
        List<com.possum.domain.model.TaxCategory> taxCategories = taxRepository.getAllTaxCategories();

        filterBar.addMultiSelectFilter("status", "Status", List.of("active", "inactive", "discontinued"), 
            item -> item.substring(0, 1).toUpperCase() + item.substring(1), false);
        filterBar.addMultiSelectFilter("taxCategory", "Tax Category", taxCategories, com.possum.domain.model.TaxCategory::name);
        filterBar.addMultiSelectFilter("categories", "Categories", categories, Category::name);

        setupStandardFilterListener((filters, reload) -> {
            @SuppressWarnings("unchecked")
            List<String> statusFilter = (List<String>) filters.get("status");
            currentStatusFilters = statusFilter != null ? statusFilter : java.util.Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<com.possum.domain.model.TaxCategory> taxFilter = (List<com.possum.domain.model.TaxCategory>) filters.get("taxCategory");
            if (taxFilter != null) {
                currentTaxCategoryFilters = taxFilter.stream().map(com.possum.domain.model.TaxCategory::id).toList();
            } else {
                currentTaxCategoryFilters = java.util.Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Category> cats = (List<Category>) filters.get("categories");
            if (cats != null) {
                currentCategoryFilters = cats.stream().map(Category::id).toList();
            } else {
                currentCategoryFilters = java.util.Collections.emptyList();
            }

            reload.run();
        });
    }


    @Override
    protected ProductFilter buildFilter() {
        return new ProductFilter(
            getSearchOrNull(),
            currentTaxCategoryFilters.isEmpty() ? null : currentTaxCategoryFilters,
            currentStatusFilters.isEmpty() ? null : currentStatusFilters,
            currentCategoryFilters.isEmpty() ? null : currentCategoryFilters,
            getCurrentPage(),
            getPageSize(),
            "name",
            "ASC"
        );
    }

    @Override
    protected PagedResult<Product> fetchData(ProductFilter filter) {
        return productService.getProducts(filter);
    }

    protected void loadData() {
        super.loadData();
    }

    @Override
    protected String getEntityName() {
        return "products";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Product";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Product product) {
        return com.possum.ui.common.components.MenuBuilder.create()
            .addViewAction("View Details", () -> workspaceManager.openWindow(
                "View Product: " + product.name(), 
                "/fxml/products/product-form-view.fxml", 
                Map.of("productId", product.id(), "mode", "view")))
            .addEditAction("Edit Product", () -> workspaceManager.openWindow(
                "Edit Product: " + product.name(), 
                "/fxml/products/product-form-view.fxml", 
                Map.of("productId", product.id(), "mode", "edit")))
            .addSeparator()
            .addDeleteAction("Delete Product", () -> handleDelete(product))
            .build();
    }

    @Override
    protected void deleteEntity(Product entity) throws Exception {
        long userId = AuthContext.getCurrentUser().id();
        productService.deleteProduct(entity.id(), userId);
    }

    @Override
    protected String getEntityIdentifier(Product entity) {
        return entity.name();
    }

    @FXML
    private void handleAdd() {
        workspaceManager.openWindow("Add Product", "/fxml/products/product-form-view.fxml");
    }

    @FXML
    private void handleImport() {
        importHandler.handleImport();
    }

    /**
     * Inner class to handle CSV import functionality
     */
    private class ImportHandler extends AbstractImportController<Product, ProductImportRow> {

        @Override
        protected String[] getRequiredHeaders() {
            return new String[]{"Product Name", "Name"};
        }

        @Override
        protected ProductImportRow parseRow(List<String> row, Map<String, Integer> headers) {
            String name = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Product Name", "Name"));
            if (name == null || "No Of Products".equalsIgnoreCase(name)) {
                return null;
            }

            String sku = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Product Code", "SKU"));
            String categoryName = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(
                row, headers, "Division Name", "Category Name", "Category"
            ));

            Integer stockAlert = CsvImportUtil.parseInteger(
                CsvImportUtil.getValue(row, headers, "Minimum Stock Level", "Stock Alert", "Stock Alert Cap"), 0
            );
            if (stockAlert == null || stockAlert < 0) stockAlert = 0;

            BigDecimal price = CsvImportUtil.parseDecimal(
                CsvImportUtil.getValue(row, headers, "MRP", "MRP/Price", "Price"), BigDecimal.ZERO
            );
            if (price.compareTo(BigDecimal.ZERO) < 0) price = BigDecimal.ZERO;

            BigDecimal costPrice = CsvImportUtil.parseDecimal(
                CsvImportUtil.getValue(row, headers, "Avg Item Cost", "Cost Price", "Cost"), BigDecimal.ZERO
            );
            if (costPrice.compareTo(BigDecimal.ZERO) < 0) costPrice = BigDecimal.ZERO;

            return new ProductImportRow(name, sku, categoryName, stockAlert, price, costPrice);
        }

        @Override
        protected Product createEntity(ProductImportRow record) throws Exception {
            List<Category> allCategories = categoryService.getAllCategories();
            Map<String, Long> categoryMap = new HashMap<>();
            for (Category c : allCategories) {
                categoryMap.put(c.name().trim().toLowerCase(Locale.ROOT), c.id());
            }

            Long categoryId = resolveOrCreateCategoryId(record.categoryName(), categoryMap);
            long actorId = AuthContext.getCurrentUser().id();

            ProductService.VariantCommand defaultVariant = new ProductService.VariantCommand(
                null, record.name(), record.sku(), record.price(), record.costPrice(),
                record.stockAlert(), true, "active", 0, "Initial import"
            );

            productService.createProductWithVariants(
                new ProductService.CreateProductCommand(
                    record.name(), "", categoryId, "active", null,
                    List.of(defaultVariant), java.util.Collections.emptyList(), actorId
                )
            );
            return null;
        }

        @Override
        protected String getImportTitle() {
            return "Import Products from CSV";
        }

        @Override
        protected String getEntityName() {
            return "product(s)";
        }

        @Override
        protected void onImportComplete() {
            loadData();
        }

        private Long resolveOrCreateCategoryId(String categoryName, Map<String, Long> categoryMap) {
            if (categoryName == null || categoryName.isBlank()) return null;

            String key = categoryName.trim().toLowerCase(Locale.ROOT);
            Long existingId = categoryMap.get(key);
            if (existingId != null) return existingId;

            Category created = categoryService.createCategory(categoryName.trim(), null);
            categoryMap.put(key, created.id());
            return created.id();
        }
    }

    private record ProductImportRow(
        String name, String sku, String categoryName,
        Integer stockAlert, BigDecimal price, BigDecimal costPrice
    ) {}
}

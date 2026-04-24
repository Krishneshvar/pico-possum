package com.picopossum.ui.products;

import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.products.ProductService;
import com.picopossum.domain.model.Category;
import com.picopossum.domain.model.Product;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.ui.common.controllers.AbstractCrudController;
import com.picopossum.ui.common.controllers.AbstractImportController;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.infrastructure.system.AppExecutor;
import com.picopossum.domain.model.ProductStatus;
import com.picopossum.shared.util.CsvImportUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProductsController extends AbstractCrudController<Product, ProductFilter> {

    @FXML
    private Button addButton;
    @FXML
    private Button refreshButton;

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ImportHandler importHandler;

    private List<String> currentStatusFilters = java.util.Collections.emptyList();
    private List<String> currentStockStatusFilters = java.util.Collections.emptyList();
    private List<Long> currentCategoryFilters = java.util.Collections.emptyList();
    private BigDecimal currentMinPrice = null;
    private BigDecimal currentMaxPrice = null;

    public ProductsController(ProductService productService,
            CategoryService categoryService,
            WorkspaceManager workspaceManager,
            AppExecutor executor) {
        super(workspaceManager, executor);
        this.productService = productService;
        this.categoryService = categoryService;
        this.importHandler = new ImportHandler();
    }

    @Override
    public void initialize() {
        initUIComponents();
        setupTable();
        setupFilters();
        loadData();
    }

    @Override
    protected void initUIComponents() {
        if (addButton != null) {
            FontIcon addIcon = new FontIcon("bx-plus");
            addIcon.setIconSize(16);
            addIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            addButton.setGraphic(addIcon);
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
        dataTable.getTableView()
                .setPlaceholder(new Label("No products found. Adjust filters or click + Add Product to create one."));
        dataTable.setEmptyMessage("No products found");
        dataTable.setEmptySubtitle("Try changing filters or create your first product.");

        TableColumn<Product, String> skuCol = new TableColumn<>("SKU");
        skuCol.setId("sku");
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));

        TableColumn<Product, String> nameCol = new TableColumn<>("Name");
        nameCol.setId("name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));

        TableColumn<Product, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setId("price");
        priceCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().mrp()));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(com.picopossum.shared.util.CurrencyUtil.format(item));
                }
            }
        });

        TableColumn<Product, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setId("stock");
        stockCol.setPrefWidth(120);
        stockCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().stock()));
        stockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Product p = getTableRow().getItem();
                    if (p != null) {
                        setGraphic(com.picopossum.ui.common.components.BadgeFactory.createStockBadge(p.stock(),
                                p.stockAlertCap()));
                        setText(null);
                    } else {
                        setText(String.valueOf(item));
                        setGraphic(null);
                    }
                }
            }
        });

        TableColumn<Product, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setId("category_name");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().categoryName()));

        TableColumn<Product, String> statusCol = new TableColumn<>("Status");
        statusCol.setSortable(false);
        statusCol.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().status().name().toLowerCase()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(com.picopossum.ui.common.components.BadgeFactory.createProductStatusBadge(status));
                    setText(null);
                }
            }
        });

        dataTable.getTableView().getColumns().addAll(skuCol, nameCol, priceCol, stockCol, categoryCol, statusCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        List<Category> categories = categoryService.getAllCategories();

        filterBar.addMultiSelectFilter("status", "Status", List.of("active", "inactive", "discontinued"),
                item -> item.substring(0, 1).toUpperCase() + item.substring(1), false);

        filterBar.addMultiSelectFilter("stockStatus", "Stock Status", List.of("in-stock", "low-stock", "out-of-stock"),
                item -> com.picopossum.shared.util.TextFormatter.toTitleCase(item.replace("-", " ")), false);

        filterBar.addMultiSelectFilter("categories", "Categories", categories, Category::name);

        filterBar.addTextFilter("minPrice", "Min Price");
        filterBar.addTextFilter("maxPrice", "Max Price");

        setupStandardFilterListener((filters, reload) -> {
            @SuppressWarnings("unchecked")
            List<String> statusFilter = (List<String>) filters.get("status");
            currentStatusFilters = statusFilter != null ? statusFilter : java.util.Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<String> stockStatusFilter = (List<String>) filters.get("stockStatus");
            currentStockStatusFilters = stockStatusFilter != null ? stockStatusFilter
                    : java.util.Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Category> cats = (List<Category>) filters.get("categories");
            if (cats != null) {
                currentCategoryFilters = cats.stream().map(Category::id).toList();
            } else {
                currentCategoryFilters = java.util.Collections.emptyList();
            }

            currentMinPrice = parseBigDecimal(filters.get("minPrice"));
            currentMaxPrice = parseBigDecimal(filters.get("maxPrice"));

            reload.run();
        });
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null)
            return null;
        if (value instanceof BigDecimal)
            return (BigDecimal) value;
        try {
            String s = value.toString().replaceAll("[^0-9.\\-]", "");
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected ProductFilter buildFilter() {
        return new ProductFilter(
                getSearchOrNull(),
                currentStatusFilters.isEmpty() ? null : currentStatusFilters,
                currentCategoryFilters.isEmpty() ? null : currentCategoryFilters,
                currentStockStatusFilters.isEmpty() ? null : currentStockStatusFilters,
                currentMinPrice,
                currentMaxPrice,
                getCurrentPage(),
                getPageSize(),
                getSortBy() != null ? getSortBy() : "name",
                getSortDirection());
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
        return com.picopossum.ui.common.components.MenuBuilder.create()
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
        productService.deleteProduct(entity.id());
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

    @FXML
    protected void handleRefresh() {
        // Refresh categories list in filter
        List<Category> categories = categoryService.getAllCategories();
        filterBar.updateMultiSelectFilterItems("categories", categories);

        loadData();
        com.picopossum.ui.common.controls.NotificationService.success("Products refreshed");
    }

    /**
     * Inner class to handle CSV import functionality
     */
    private class ImportHandler extends AbstractImportController<Product, ProductImportRow> {

        @Override
        protected String[] getRequiredHeaders() {
            return new String[] { "Product Name", "Name" };
        }

        @Override
        protected ProductImportRow parseRow(List<String> row, Map<String, Integer> headers) {
            String name = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Product Name", "Name"));
            if (name == null || "No Of Products".equalsIgnoreCase(name)) {
                return null;
            }

            String sku = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Product Code", "SKU"));
            String categoryName = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(
                    row, headers, "Division Name", "Category Name", "Category"));

            Integer stockAlert = CsvImportUtil.parseInteger(
                    CsvImportUtil.getValue(row, headers, "Minimum Stock Level", "Stock Alert", "Stock Alert Cap"), 10);

            BigDecimal price = CsvImportUtil.parseDecimal(
                    CsvImportUtil.getValue(row, headers, "MRP", "MRP/Price", "Price"), BigDecimal.ZERO);

            BigDecimal costPrice = CsvImportUtil.parseDecimal(
                    CsvImportUtil.getValue(row, headers, "Avg Item Cost", "Cost Price", "Cost"), BigDecimal.ZERO);

            Integer initialStock = CsvImportUtil.parseInteger(
                    CsvImportUtil.getValue(row, headers, "Opening Stock", "Current Stock", "Stock"), 0);

            BigDecimal taxRate = CsvImportUtil.parseDecimal(
                    CsvImportUtil.getValue(row, headers, "Tax Rate", "Tax%"), BigDecimal.ZERO);
            String barcode = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Barcode", "EAN"));

            return new ProductImportRow(name, sku, categoryName, stockAlert, price, costPrice, initialStock, taxRate,
                    barcode);
        }

        @Override
        protected Product createEntity(ProductImportRow record) throws Exception {
            List<Category> allCategories = categoryService.getAllCategories();
            Map<String, Long> categoryMap = new HashMap<>();
            for (Category c : allCategories) {
                categoryMap.put(c.name().trim().toLowerCase(Locale.ROOT), c.id());
            }

            Long categoryId = resolveOrCreateCategoryId(record.categoryName(), categoryMap);

            productService.createProduct(
                    new ProductService.CreateProductCommand(
                            record.name(), "", categoryId, record.sku(),
                            record.price(), record.costPrice(), record.stockAlert(), ProductStatus.ACTIVE,
                            null, record.initialStock(), record.taxRate(), record.barcode()));
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
            handleRefresh();
        }

        private Long resolveOrCreateCategoryId(String categoryName, Map<String, Long> categoryMap) {
            if (categoryName == null || categoryName.isBlank())
                return null;

            String key = categoryName.trim().toLowerCase(Locale.ROOT);
            Long existingId = categoryMap.get(key);
            if (existingId != null)
                return existingId;

            Category created = categoryService.createCategory(categoryName.trim(), null);
            categoryMap.put(key, created.id());
            return created.id();
        }
    }

    private record ProductImportRow(
            String name, String sku, String categoryName,
            Integer stockAlert, BigDecimal price, BigDecimal costPrice,
            Integer initialStock, BigDecimal taxRate, String barcode) {
    }
}

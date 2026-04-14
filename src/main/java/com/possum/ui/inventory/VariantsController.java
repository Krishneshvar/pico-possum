package com.possum.ui.inventory;

import com.possum.application.categories.CategoryService;
import com.possum.domain.model.Category;
import com.possum.domain.model.Variant;
import com.possum.domain.repositories.VariantRepository;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VariantsController {

    @FXML private FilterBar filterBar;
    @FXML private DataTableView<Variant> variantsTable;
    @FXML private PaginationBar paginationBar;
    @FXML private Button refreshButton;

    private final VariantRepository variantRepository;
    private final CategoryService categoryService;
    private final WorkspaceManager workspaceManager;
    private String currentSearch = "";
    private List<Long> currentTaxCategoryFilters = Collections.emptyList();
    private List<String> currentStatusFilters = Collections.emptyList();
    private List<String> currentStockFilters = Collections.emptyList();
    private List<Long> currentCategoryFilters = Collections.emptyList();
    private java.math.BigDecimal currentMinPrice = null;
    private java.math.BigDecimal currentMaxPrice = null;
    private final com.possum.domain.repositories.TaxRepository taxRepository;

    public VariantsController(VariantRepository variantRepository, CategoryService categoryService, 
                              com.possum.domain.repositories.TaxRepository taxRepository,
                              WorkspaceManager workspaceManager) {
        this.variantRepository = variantRepository;
        this.categoryService = categoryService;
        this.taxRepository = taxRepository;
        this.workspaceManager = workspaceManager;
    }

    @FXML
    public void initialize() {
        variantsTable.getTableView().setPlaceholder(new javafx.scene.control.Label("No variants found. Try adjusting filters or search terms."));
        variantsTable.setEmptyMessage("No variants found");
        variantsTable.setEmptySubtitle("Update search or filters to locate product variants.");
        if (refreshButton != null) {
            FontIcon refreshIcon = new FontIcon("bx-sync");
            refreshIcon.setIconSize(16);
            refreshButton.setGraphic(refreshIcon);
            refreshButton.setText("Refresh");
        }
        setupTable();
        setupFilters();
        loadVariants();
    }


    @FXML
    private void handleRefresh() {
        loadVariants();
    }

    private void setupFilters() {
        List<Category> categories = categoryService.getAllCategories();
        List<com.possum.domain.model.TaxCategory> taxCategories = taxRepository.getAllTaxCategories();
        
        filterBar.addMultiSelectFilter("status", "Status", List.of("active", "inactive", "discontinued"),
            item -> item.substring(0, 1).toUpperCase() + item.substring(1), false);
        filterBar.addMultiSelectFilter("stockStatus", "Stock Status", List.of("in-stock", "low-stock", "out-of-stock"),
            item -> java.util.Arrays.stream(item.split("-")).map(word -> word.substring(0, 1).toUpperCase() + word.substring(1)).collect(java.util.stream.Collectors.joining(" ")), false);
        filterBar.addMultiSelectFilter("taxCategory", "Tax Category", taxCategories, com.possum.domain.model.TaxCategory::name);
        filterBar.addMultiSelectFilter("categories", "Categories", categories, Category::name);
        filterBar.addTextFilter("minPrice", "Min MRP");
        filterBar.addTextFilter("maxPrice", "Max MRP");

        filterBar.setOnFilterChange(filters -> {
            currentSearch = (String) filters.get("search");

            @SuppressWarnings("unchecked")
            List<String> statusFilter = (List<String>) filters.get("status");
            currentStatusFilters = statusFilter != null ? statusFilter : Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<String> stockFilter = (List<String>) filters.get("stockStatus");
            currentStockFilters = stockFilter != null ? stockFilter : Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<com.possum.domain.model.TaxCategory> taxFilterList = (List<com.possum.domain.model.TaxCategory>) filters.get("taxCategory");
            if (taxFilterList != null) {
                currentTaxCategoryFilters = taxFilterList.stream().map(com.possum.domain.model.TaxCategory::id).toList();
            } else {
                currentTaxCategoryFilters = Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Category> cats = (List<Category>) filters.get("categories");
            if (cats != null) {
                currentCategoryFilters = cats.stream().map(Category::id).toList();
            } else {
                currentCategoryFilters = Collections.emptyList();
            }

            currentMinPrice = parseBigDecimal(filters.get("minPrice"));
            currentMaxPrice = parseBigDecimal(filters.get("maxPrice"));

            loadVariants();
        });

        paginationBar.setOnPageChange((page, size) -> loadVariants());
    }

    private void loadVariants() {
        variantsTable.setLoading(true);

        CompletableFuture.supplyAsync(() -> {
            return variantRepository.findVariants(
                currentSearch == null || currentSearch.isEmpty() ? null : currentSearch,
                null,
                currentCategoryFilters.isEmpty() ? null : currentCategoryFilters,
                currentTaxCategoryFilters.isEmpty() ? null : currentTaxCategoryFilters,
                currentStockFilters.isEmpty() ? null : currentStockFilters,
                currentStatusFilters.isEmpty() ? null : currentStatusFilters,
                currentMinPrice,
                currentMaxPrice,
                "product_name",
                "ASC",
                paginationBar.getCurrentPage(),
                paginationBar.getPageSize()
            );
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                var items = FXCollections.observableArrayList(result.items());
                variantsTable.setItems(items);
                paginationBar.setTotalItems(result.totalCount());
                variantsTable.setLoading(false);
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                variantsTable.setLoading(false);
                NotificationService.error("Failed to load variants");
            });
            return null;
        });
    }

    private void setupTable() {
        TableColumn<Variant, String> productCol = new TableColumn<>("Product");
        productCol.setId("product_name");
        productCol.setSortable(true);
        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));

        TableColumn<Variant, String> variantCol = new TableColumn<>("Variant");
        variantCol.setId("name");
        variantCol.setSortable(true);
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));

        TableColumn<Variant, java.math.BigDecimal> mrpCol = new TableColumn<>("MRP");
        mrpCol.setId("price");
        mrpCol.setSortable(true);
        mrpCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().price()));

        TableColumn<Variant, String> taxCol = new TableColumn<>("Tax Category");
        taxCol.setId("tax_category_name");
        taxCol.setSortable(true);
        taxCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().taxCategoryName() != null ? cellData.getValue().taxCategoryName() : "-"));

        TableColumn<Variant, String> skuCol = new TableColumn<>("SKU");
        skuCol.setId("sku");
        skuCol.setSortable(true);
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));

        TableColumn<Variant, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setId("category_name");
        categoryCol.setSortable(true);
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().categoryName() != null ? cellData.getValue().categoryName() : ""));

        TableColumn<Variant, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setId("stock");
        stockCol.setSortable(true);
        stockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stock()));
        stockCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(stock.toString());
                    Variant variant = getTableRow() != null ? getTableRow().getItem() : null;
                    if (variant != null) {
                        int alertCap = variant.stockAlertCap() != null ? variant.stockAlertCap() : 0;
                        if (stock <= 0) {
                            setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;"); // Red
                        } else if (stock <= alertCap) {
                            setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;"); // Yellow/Orange
                        } else {
                            setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;"); // Green
                        }
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        TableColumn<Variant, String> statusCol = new TableColumn<>("Status");
        statusCol.setId("status");
        statusCol.setSortable(false);
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
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

        variantsTable.getTableView().getColumns().addAll(productCol, variantCol, skuCol, categoryCol, taxCol, mrpCol, stockCol, statusCol);

        variantsTable.addMenuActionColumn("Actions", this::buildActionsMenu);
    }


    private java.util.List<MenuItem> buildActionsMenu(Variant variant) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();

        MenuItem viewProductItem = new MenuItem("View Product");
        FontIcon viewIcon = new FontIcon("bx-show");
        viewIcon.setIconSize(14);
        viewIcon.getStyleClass().add("table-action-icon");
        viewProductItem.setGraphic(viewIcon);
        viewProductItem.setOnAction(e -> workspaceManager.openWindow("View Product", "/fxml/products/product-form-view.fxml", Map.of("productId", variant.productId(), "mode", "view")));

        MenuItem editProductItem = new MenuItem("Edit Product");
        FontIcon editIcon = new FontIcon("bx-pencil");
        editIcon.setIconSize(14);
        editIcon.getStyleClass().add("table-action-icon");
        editProductItem.setGraphic(editIcon);
        editProductItem.setOnAction(e -> workspaceManager.openWindow("Edit Product", "/fxml/products/product-form-view.fxml", Map.of("productId", variant.productId(), "mode", "edit")));

        items.addAll(java.util.Arrays.asList(viewProductItem, editProductItem));

        return items;
    }

    private java.math.BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof java.math.BigDecimal) return (java.math.BigDecimal) value;
        try {
            String s = value.toString().replaceAll("[^0-9.\\-]", "");
            return s.isEmpty() ? null : new java.math.BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}

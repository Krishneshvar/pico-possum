package com.picopossum.ui.inventory;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.model.Product;
import com.picopossum.domain.model.Category;
import com.picopossum.domain.repositories.ProductRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ProductFilter;
import com.picopossum.ui.common.controllers.AbstractCrudController;
import com.picopossum.ui.common.components.BadgeFactory;
import com.picopossum.ui.common.components.ButtonFactory;
import com.picopossum.ui.common.controls.FormDialog;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.ui.workspace.WorkspaceManager;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import com.picopossum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;

public class InventoryController extends AbstractCrudController<Product, ProductFilter> {
    
    @FXML private Button refreshButton;
    
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    
    private List<Long> currentCategoryFilters = List.of();
    private List<String> currentStockFilters = List.of();
    private List<String> currentStatusFilters = List.of();
    private BigDecimal currentMinPrice = null;
    private BigDecimal currentMaxPrice = null;

    public InventoryController(InventoryService inventoryService, 
                               ProductRepository productRepository,
                               CategoryService categoryService,
                               WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.categoryService = categoryService;
    }

    @Override
    protected void initUIComponents() {
        if (refreshButton != null) {
            ButtonFactory.applyRefreshButtonStyle(refreshButton);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No inventory records found");
        dataTable.setEmptySubtitle("Try broader filters or add stock to see live inventory.");
        
        TableColumn<Product, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Product, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().categoryName() != null ? cellData.getValue().categoryName() : "Uncategorized"
        ));

        TableColumn<Product, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        
        TableColumn<Product, Integer> stockCol = new TableColumn<>("Current Stock");
        stockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stock()));
        stockCol.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(5);
            private final Label textLabel = new Label();
            private final Button editBtn = ButtonFactory.createIconButton("bx-pencil", "Adjust Stock", () -> {});
            {
                editBtn.getStyleClass().add("btn-edit-stock");
                editBtn.setOnAction(e -> {
                    Product product = getTableView().getItems().get(getIndex());
                    if (product != null) {
                        handleAdjust(product);
                    }
                });
                box.getChildren().addAll(textLabel, editBtn);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                box.setSpacing(10);
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    textLabel.setText(String.valueOf(item));

                    Product product = getTableRow() != null ? getTableRow().getItem() : null;
                    if (product != null) {
                        int alertCap = product.stockAlertCap() != null ? product.stockAlertCap() : 0;
                        if (item <= 0) {
                            textLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                        } else if (item <= alertCap) {
                            textLabel.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                        } else {
                            textLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                        }
                    } else {
                        textLabel.setStyle("");
                    }

                    setGraphic(box);
                }
            }
        });
        

        TableColumn<Product, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().mrp()));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyUtil.format(item));
            }
        });
        
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
                    Label badge = BadgeFactory.createStatusBadge(status);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        productCol.setId("name");
        categoryCol.setId("category_name");
        skuCol.setId("sku");
        stockCol.setId("stock");
        priceCol.setId("price");

        dataTable.getTableView().getColumns().addAll(skuCol, productCol, priceCol, stockCol, categoryCol, statusCol);
    }

    @Override
    protected void setupFilters() {
        List<Category> categories = categoryService.getAllCategories();
        
        filterBar.addMultiSelectFilter("status", "Status", List.of("active", "inactive", "discontinued"),
            item -> item.substring(0, 1).toUpperCase() + item.substring(1).toLowerCase(), false);
        filterBar.addMultiSelectFilter("stockStatus", "Stock Status", List.of("in-stock", "low-stock", "out-of-stock"), 
            item -> java.util.Arrays.stream(item.split("-"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .collect(java.util.stream.Collectors.joining(" ")));
        filterBar.addMultiSelectFilter("categories", "Categories", categories, Category::name);
        filterBar.addTextFilter("minPrice", "Min Price");
        filterBar.addTextFilter("maxPrice", "Max Price");
        
        setupStandardFilterListener();
    }

    @Override
    protected ProductFilter buildFilter() {
        String searchTerm = filterBar.getSearchTerm();

        @SuppressWarnings("unchecked")
        List<String> statusFilter = (List<String>) filterBar.getFilterValue("status");
        currentStatusFilters = statusFilter != null ? statusFilter : List.of();

        @SuppressWarnings("unchecked")
        List<String> stockFilter = (List<String>) filterBar.getFilterValue("stockStatus");
        currentStockFilters = stockFilter != null ? stockFilter : List.of();

        @SuppressWarnings("unchecked")
        List<Category> cats = (List<Category>) filterBar.getFilterValue("categories");
        if (cats != null) {
            currentCategoryFilters = cats.stream().map(Category::id).toList();
        } else {
            currentCategoryFilters = List.of();
        }



        currentMinPrice = parseBigDecimal(filterBar.getFilterValue("minPrice"));
        currentMaxPrice = parseBigDecimal(filterBar.getFilterValue("maxPrice"));

        return new ProductFilter(
            searchTerm == null || searchTerm.isEmpty() ? null : searchTerm,
            currentStatusFilters.isEmpty() ? null : currentStatusFilters,
            currentCategoryFilters.isEmpty() ? null : currentCategoryFilters,
            currentStockFilters.isEmpty() ? null : currentStockFilters,
            currentMinPrice,
            currentMaxPrice,
            getCurrentPage(),
            getPageSize(),
            "stock",
            "ASC"
        );
    }

    @Override
    protected PagedResult<Product> fetchData(ProductFilter filter) {
        return productRepository.findProducts(filter);
    }

    @Override
    protected String getEntityName() {
        return "inventory";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Inventory Item";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Product entity) {
        return List.of(); // Inventory uses inline adjust button
    }

    @Override
    protected void deleteEntity(Product entity) throws Exception {
        throw new UnsupportedOperationException("Inventory items cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(Product entity) {
        return entity.name();
    }

    private void handleAdjust(Product product) {
        FormDialog.show("Adjust Stock", dialog -> {
            dialog.setSubtitle("Modify inventory levels for " + product.name() + ". Choose an adjustment type and enter the value below.");
            var typeCombo = dialog.addComboBox("type", "Adjustment Type", "Add/Subtract");
            typeCombo.getItems().addAll("Add/Subtract", "Set Exact");
            dialog.addTextField("quantity", "Quantity / New Stock", "0");
            var reasonCombo = dialog.addComboBox("reason", "Reason", InventoryReason.CORRECTION);
            reasonCombo.getItems().addAll(
                InventoryReason.CORRECTION,
                InventoryReason.DAMAGE,
                InventoryReason.SPOILAGE,
                InventoryReason.THEFT
            );
        }, values -> {
            try {
                int inputValue = Integer.parseInt((String) values.get("quantity"));
                String type = (String) values.get("type");
                int currentStock = product.stock() != null ? product.stock() : 0;
                int quantity = "Set Exact".equals(type) ? (inputValue - currentStock) : inputValue;

                InventoryReason reason = (InventoryReason) values.get("reason");
                long userId = AuthContext.getCurrentUser().id();
                
                inventoryService.adjustInventory(
                    product.id(),
                    null,
                    quantity,
                    reason,
                    "manual_adjustment",
                    null,
                    userId
                );
                
                NotificationService.success("Stock adjusted successfully");
                loadData();
            } catch (NumberFormatException e) {
                NotificationService.error("Invalid quantity");
            } catch (Exception e) {
                NotificationService.error("Failed to adjust stock: " + e.getMessage());
            }
        });
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            String s = value.toString().replaceAll("[^0-9.\\-]", "");
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    @FXML
    protected void handleRefresh() {
        loadData();
        NotificationService.success("Inventory list refreshed");
    }
}

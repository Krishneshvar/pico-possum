package com.possum.ui.inventory;

import com.possum.application.auth.AuthContext;
import com.possum.application.inventory.InventoryService;
import com.possum.application.categories.CategoryService;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.model.Variant;
import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.Category;
import com.possum.domain.repositories.VariantRepository;
import com.possum.domain.repositories.TaxRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.components.BadgeFactory;
import com.possum.ui.common.components.ButtonFactory;
import com.possum.ui.common.controls.FormDialog;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;

public class InventoryController extends AbstractCrudController<Variant, InventoryFilter> {
    
    @FXML private Button refreshButton;
    
    private final InventoryService inventoryService;
    private final VariantRepository variantRepository;
    private final CategoryService categoryService;
    private final TaxRepository taxRepository;
    
    private List<Long> currentCategoryFilters = List.of();
    private List<Long> currentTaxCategoryFilters = List.of();
    private List<String> currentStockFilters = List.of();
    private List<String> currentStatusFilters = List.of();
    private BigDecimal currentMinPrice = null;
    private BigDecimal currentMaxPrice = null;

    public InventoryController(InventoryService inventoryService, 
                               VariantRepository variantRepository,
                               CategoryService categoryService,
                               TaxRepository taxRepository,
                               WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.inventoryService = inventoryService;
        this.variantRepository = variantRepository;
        this.categoryService = categoryService;
        this.taxRepository = taxRepository;
    }

    @Override
    protected void setupPermissions() {
        if (refreshButton != null) {
            ButtonFactory.applyRefreshButtonStyle(refreshButton);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No inventory records found");
        dataTable.setEmptySubtitle("Try broader filters or add stock to see live inventory.");
        
        TableColumn<Variant, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        
        TableColumn<Variant, String> variantCol = new TableColumn<>("Variant");
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Variant, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().categoryName() != null ? cellData.getValue().categoryName() : "Uncategorized"
        ));

        TableColumn<Variant, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        
        TableColumn<Variant, Integer> stockCol = new TableColumn<>("Current Stock");
        stockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().stock()));
        stockCol.setCellFactory(col -> new TableCell<>() {
            private final HBox box = new HBox(5);
            private final Label textLabel = new Label();
            private final Button editBtn = ButtonFactory.createIconButton("bx-pencil", "Adjust Stock", () -> {});
            {
                editBtn.getStyleClass().add("btn-edit-stock");
                editBtn.setOnAction(e -> {
                    Variant variant = getTableView().getItems().get(getIndex());
                    if (variant != null) {
                        handleAdjust(variant);
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

                    Variant variant = getTableRow() != null ? getTableRow().getItem() : null;
                    if (variant != null) {
                        int alertCap = variant.stockAlertCap() != null ? variant.stockAlertCap() : 0;
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
        
        TableColumn<Variant, String> taxCategoryCol = new TableColumn<>("Tax Category");
        taxCategoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().taxCategoryName() != null ? cellData.getValue().taxCategoryName() : "-"
        ));
        
        TableColumn<Variant, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().price()));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyUtil.format(item));
            }
        });
        
        TableColumn<Variant, String> statusCol = new TableColumn<>("Status");
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

        productCol.setId("product_name");
        variantCol.setId("name");
        categoryCol.setId("category_name");
        taxCategoryCol.setId("tax_category_name");
        skuCol.setId("sku");
        stockCol.setId("stock");
        priceCol.setId("price");

        dataTable.getTableView().getColumns().addAll(productCol, variantCol, skuCol, categoryCol, taxCategoryCol, priceCol, stockCol, statusCol);
    }

    @Override
    protected void setupFilters() {
        List<Category> categories = categoryService.getAllCategories();
        List<TaxCategory> taxCategories = taxRepository.getAllTaxCategories();
        
        filterBar.addMultiSelectFilter("status", "Status", List.of("active", "inactive", "discontinued"),
            item -> item.substring(0, 1).toUpperCase() + item.substring(1).toLowerCase(), false);
        filterBar.addMultiSelectFilter("stockStatus", "Stock Status", List.of("in-stock", "low-stock", "out-of-stock"), 
            item -> java.util.Arrays.stream(item.split("-"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .collect(java.util.stream.Collectors.joining(" ")));
        filterBar.addMultiSelectFilter("categories", "Categories", categories, Category::name);
        filterBar.addMultiSelectFilter("taxCategories", "Tax Categories", taxCategories, TaxCategory::name);
        filterBar.addTextFilter("minPrice", "Min Price");
        filterBar.addTextFilter("maxPrice", "Max Price");
    }

    @Override
    protected InventoryFilter buildFilter() {
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

        @SuppressWarnings("unchecked")
        List<TaxCategory> tcs = (List<TaxCategory>) filterBar.getFilterValue("taxCategories");
        if (tcs != null) {
            currentTaxCategoryFilters = tcs.stream().map(TaxCategory::id).toList();
        } else {
            currentTaxCategoryFilters = List.of();
        }

        currentMinPrice = parseBigDecimal(filterBar.getFilterValue("minPrice"));
        currentMaxPrice = parseBigDecimal(filterBar.getFilterValue("maxPrice"));

        return new InventoryFilter(
            searchTerm == null || searchTerm.isEmpty() ? null : searchTerm,
            currentCategoryFilters.isEmpty() ? null : currentCategoryFilters,
            currentTaxCategoryFilters.isEmpty() ? null : currentTaxCategoryFilters,
            currentStockFilters.isEmpty() ? null : currentStockFilters,
            currentStatusFilters.isEmpty() ? null : currentStatusFilters,
            currentMinPrice,
            currentMaxPrice,
            getCurrentPage(),
            getPageSize()
        );
    }

    @Override
    protected PagedResult<Variant> fetchData(InventoryFilter filter) {
        return variantRepository.findVariants(
            filter.searchTerm(),
            null,
            filter.categoryIds(),
            filter.taxCategoryIds(),
            filter.stockStatuses(),
            filter.statuses(),
            filter.minPrice(),
            filter.maxPrice(),
            "stock",
            "ASC",
            filter.page(),
            filter.limit()
        );
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
    protected List<MenuItem> buildActionMenu(Variant entity) {
        return List.of(); // Inventory uses inline adjust button
    }

    @Override
    protected void deleteEntity(Variant entity) throws Exception {
        throw new UnsupportedOperationException("Inventory items cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(Variant entity) {
        return entity.productName() + " (" + entity.name() + ")";
    }

    private void handleAdjust(Variant variant) {
        FormDialog.show("Adjust Stock", dialog -> {
            dialog.setSubtitle("Modify inventory levels for " + variant.productName() + " (" + variant.name() + "). " +
                             "Choose an adjustment type and enter the value below.");
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
                int currentStock = variant.stock() != null ? variant.stock() : 0;
                int quantity = "Set Exact".equals(type) ? (inputValue - currentStock) : inputValue;

                InventoryReason reason = (InventoryReason) values.get("reason");
                long userId = AuthContext.getCurrentUser().id();
                
                inventoryService.adjustInventory(
                    variant.id(),
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
}

// Filter record for inventory
record InventoryFilter(
    String searchTerm,
    List<Long> categoryIds,
    List<Long> taxCategoryIds,
    List<String> stockStatuses,
    List<String> statuses,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    int page,
    int limit
) {}

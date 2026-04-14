package com.possum.ui.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.categories.CategoryService;
import com.possum.application.products.ProductService;
import com.possum.domain.model.Category;
import com.possum.domain.model.TaxCategory;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.domain.repositories.TaxRepository;
import com.possum.ui.common.ErrorHandler;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.controls.SingleSelectFilter;
import com.possum.ui.sales.ProductSearchIndex;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.navigation.Parameterizable;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductFormController implements Parameterizable {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final TaxRepository taxRepository;
    private final WorkspaceManager workspaceManager;
    private final SettingsStore settingsStore;
    private final ProductSearchIndex productSearchIndex;
    private final com.possum.application.drafts.DraftService draftService;

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private SingleSelectFilter<CategoryItem> categoryFilter;
    @FXML private ComboBox<String> statusCombo;
    @FXML private SingleSelectFilter<TaxCategoryItem> taxFilter;
    @FXML private VBox variantsContainer;
    @FXML private Button saveButton;
    @FXML private Button addVariantButton;

    private Long productId = null;
    private final List<ProductVariantRow> variantRows = new ArrayList<>();
    private final ToggleGroup defaultVariantGroup = new ToggleGroup();
    private boolean numericSkuGenerationEnabled = false;

    public ProductFormController(ProductService productService,
                                 CategoryService categoryService,
                                 TaxRepository taxRepository,
                                 WorkspaceManager workspaceManager,
                                 SettingsStore settingsStore,
                                 ProductSearchIndex productSearchIndex,
                                 com.possum.application.drafts.DraftService draftService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.taxRepository = taxRepository;
        this.workspaceManager = workspaceManager;
        this.settingsStore = settingsStore;
        this.productSearchIndex = productSearchIndex;
        this.draftService = draftService;
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params != null && params.containsKey("productId")) {
            this.productId = (Long) params.get("productId");
            String mode = (String) params.get("mode");
            boolean isView = "view".equals(mode);

            titleLabel.setText(isView ? "View Product" : "Edit Product");
            loadProductDetails(isView);
        } else {
            this.productId = null;
            titleLabel.setText("Add Product");
            if (variantRows.isEmpty()) {
                if (!recoverDraft()) {
                    addVariantRow(true, "Default");
                }
            }
        }
    }

    private boolean recoverDraft() {
        if (draftService == null) return false;
        return draftService.recoverDraft("product_new", ProductService.CreateProductCommand.class).map(draft -> {
            nameField.setText(draft.name() != null ? draft.name() : "");
            descriptionField.setText(draft.description() != null ? draft.description() : "");
            if (draft.categoryId() != null) {
                try {
                    com.possum.domain.model.Category c = categoryService.getCategoryById(draft.categoryId());
                    categoryFilter.setSelectedItem(new CategoryItem(c.id(), c.name()));
                } catch (Exception ignored) {}
            }
            statusCombo.setValue(draft.status() != null ? draft.status() : "active");
            
            variantRows.clear();
            variantsContainer.getChildren().clear();
            if (draft.variants() != null) {
                for (var v : draft.variants()) {
                    ProductVariantRow row = new ProductVariantRow(Boolean.TRUE.equals(v.isDefault()), v.name(), defaultVariantGroup, this::removeVariantRow);
                    row.setSku(v.sku());
                    row.setPrice(v.price() != null ? v.price().toString() : "");
                    row.setCostPrice(v.costPrice() != null ? v.costPrice().toString() : "");
                    row.setStockAlert(v.stockAlertCap() != null ? v.stockAlertCap().toString() : "");
                    row.setInitialStock(v.stock() != null ? v.stock() : 0);
                    variantRows.add(row);
                    variantsContainer.getChildren().add(row.getView());
                    attachListenersToRow(row);
                }
            }
            NotificationService.success("Unsaved product draft restored.");
            return true;
        }).orElse(false);
    }

    private void loadProductDetails(boolean isView) {
        try {
            ProductService.ProductWithVariantsDTO dto = productService.getProductWithVariants(productId);

            nameField.setText(dto.product().name());
            descriptionField.setText(dto.product().description());

            if (dto.product().categoryId() != null) {
                categoryFilter.setSelectedItem(new CategoryItem(dto.product().categoryId(), dto.product().categoryName()));
            }

            statusCombo.setValue(dto.product().status() != null ? dto.product().status() : "active");

            if (dto.product().taxCategoryId() != null) {
                taxFilter.setSelectedItem(new TaxCategoryItem(dto.product().taxCategoryId(), dto.product().taxCategoryName()));
            }

            variantRows.clear();
            variantsContainer.getChildren().clear();

            if (dto.variants() != null && !dto.variants().isEmpty()) {
                for (com.possum.domain.model.Variant v : dto.variants()) {
                    ProductVariantRow row = new ProductVariantRow(Boolean.TRUE.equals(v.defaultVariant()), null, defaultVariantGroup, this::removeVariantRow);
                    row.setVariantId(v.id());
                    row.setVariantName(v.name());
                    row.setSku(v.sku() != null ? v.sku() : "");
                    if (numericSkuGenerationEnabled) {
                        row.setSkuReadOnly();
                    }
                    row.setPrice(v.price() != null ? v.price().toString() : "");
                    row.setCostPrice(v.costPrice() != null ? v.costPrice().toString() : "");
                    row.setStockAlert(v.stockAlertCap() != null ? v.stockAlertCap().toString() : "");
                    row.setVariantStatus(v.status() != null ? v.status() : "active");
                    row.setInitialStock(v.stock() != null ? v.stock() : 0);

                    if (isView) {
                        row.setReadOnly();
                    }

                    variantRows.add(row);
                    variantsContainer.getChildren().add(row.getView());
                }
            } else {
                addVariantRow(true, "Default");
            }

            if (numericSkuGenerationEnabled) {
                refreshAutoGeneratedSkusForNewRows();
            }

            if (isView) {
                replaceFieldWithLabel(nameField, dto.product().name());
                replaceFieldWithLabel(descriptionField, dto.product().description());
                
                String catName = categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().name() : "None";
                replaceFieldWithLabel(categoryFilter, catName);
                
                replaceFieldWithLabel(statusCombo, formatStatus(statusCombo.getValue()));
                
                String taxName = taxFilter.getSelectedItem() != null ? taxFilter.getSelectedItem().name() : "None";
                replaceFieldWithLabel(taxFilter, taxName);

                saveButton.setVisible(false);
                saveButton.setManaged(false);
                addVariantButton.setVisible(false);
                addVariantButton.setManaged(false);
            }

        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to load product details: {}", e.getMessage(), e);
            NotificationService.error("Failed to load product details: " + ErrorHandler.toUserMessage(e));
        }
    }

    @FXML
    public void initialize() {
        if (addVariantButton != null) {
            FontIcon plusIcon = new FontIcon("bx-plus");
            plusIcon.setIconSize(16);
            plusIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            addVariantButton.setGraphic(plusIcon);
            addVariantButton.setText("Add Variant");
        }

        if (descriptionField != null) {
            descriptionField.setPrefHeight(180);
            descriptionField.setMinHeight(150);
        }

        loadCategories();
        loadTaxCategories();
        loadSkuGenerationSettings();

        statusCombo.setItems(FXCollections.observableArrayList("active", "inactive", "discontinued"));
        statusCombo.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String object) {
                if (object == null || object.isEmpty()) return "";
                return object.substring(0, 1).toUpperCase() + object.substring(1).toLowerCase();
            }
            @Override
            public String fromString(String string) {
                if (string == null || string.isEmpty()) return "";
                return string.toLowerCase();
            }
        });
        statusCombo.setValue("active");

        statusCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateVariantStatusesBasedOnProductStatus(newVal);
        });

        setupValidation();
        setupDrafting();
    }

    private void setupDrafting() {
        if (productId != null) return; // Only draft new products for now
        
        nameField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        descriptionField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        categoryFilter.selectedItemProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        statusCombo.valueProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        taxFilter.selectedItemProperty().addListener((o, old, newVal) -> saveCurrentDraft());
    }

    private void saveCurrentDraft() {
        if (productId != null || draftService == null) return;
        
        long userId = com.possum.application.auth.AuthContext.getCurrentUser().id();
        List<ProductService.VariantCommand> variants = variantRows.stream().map(row -> 
            new ProductService.VariantCommand(null, row.getName(), row.getSku(), 
                parseSafeBigDecimal(row.getPrice()), parseSafeBigDecimal(row.getCostPrice()),
                parseSafeInt(row.getStockAlert()), row.isDefault(), row.getStatus(), row.getStock(), null)
        ).toList();

        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
            nameField.getText(), descriptionField.getText(), 
            categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
            statusCombo.getValue(), null, variants, 
            taxFilter.getSelectedItem() != null ? List.of(taxFilter.getSelectedItem().id()) : List.of(),
            userId
        );

        draftService.saveDraft("product_new", "product", cmd, userId);
    }

    private java.math.BigDecimal parseSafeBigDecimal(String val) {
        try { 
            String clean = val.replace(com.possum.shared.util.CurrencyUtil.getSymbol(), "").replace(",", "").trim();
            return new java.math.BigDecimal(clean); 
        } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    private Integer parseSafeInt(String val) {
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
    }

    private void setupValidation() {
        com.possum.ui.common.validation.FieldValidator.of(nameField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Product Name"))
                .validateOnType();
    }

    private void updateVariantStatusesBasedOnProductStatus(String productStatus) {
        if (productStatus == null) return;
        boolean isActive = "active".equals(productStatus.toLowerCase());

        for (ProductVariantRow row : variantRows) {
            row.updateAvailableStatuses(isActive);
        }
    }

    private void loadCategories() {
        List<Category> categories = categoryService.getAllCategories();
        List<CategoryItem> items = categories.stream()
                .map(c -> new CategoryItem(c.id(), c.name()))
                .toList();

        categoryFilter.setItems(items);
    }

    private void loadTaxCategories() {
        List<TaxCategory> taxes = taxRepository.getAllTaxCategories();
        List<TaxCategoryItem> items = taxes.stream()
                .map(t -> new TaxCategoryItem(t.id(), t.name()))
                .toList();
        taxFilter.setItems(items);
    }

    @FXML
    private void handleAddVariant() {
        addVariantRow(false, null);
    }

    private void addVariantRow(boolean isDefault, String name) {
        ProductVariantRow row = new ProductVariantRow(isDefault, name, defaultVariantGroup, this::removeVariantRow);

        String productStatus = statusCombo != null ? statusCombo.getValue() : "active";
        boolean isActive = productStatus == null || "active".equals(productStatus.toLowerCase());
        row.updateAvailableStatuses(isActive);

        variantRows.add(row);
        variantsContainer.getChildren().add(row.getView());

        if (numericSkuGenerationEnabled) {
            refreshAutoGeneratedSkusForNewRows();
        }
        attachListenersToRow(row);
        saveCurrentDraft();
    }

    private void attachListenersToRow(ProductVariantRow row) {
        row.variantNameField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.skuField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.priceField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.costPriceField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.stockAlertField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.stockField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.variantStatusCombo.valueProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        row.defaultRadio.selectedProperty().addListener((o, old, newVal) -> saveCurrentDraft());
    }

    private void removeVariantRow(ProductVariantRow row) {
        if (variantRows.size() > 1) {
            boolean wasDefault = row.isDefault();
            variantRows.remove(row);
            variantsContainer.getChildren().remove(row.getView());
            
            if (wasDefault && !variantRows.isEmpty()) {
                variantRows.get(0).setDefault(true);
            }

            if (numericSkuGenerationEnabled) {
                refreshAutoGeneratedSkusForNewRows();
            }
        }
    }

    private void loadSkuGenerationSettings() {
        try {
            numericSkuGenerationEnabled = settingsStore.loadGeneralSettings().isNumericalSkuGenerationEnabled();
        } catch (Exception e) {
            numericSkuGenerationEnabled = false;
        }
    }

    private void refreshAutoGeneratedSkusForNewRows() {
        if (!numericSkuGenerationEnabled) {
            for (ProductVariantRow row : variantRows) {
                row.setSkuEditable();
            }
            return;
        }

        int nextSku = productService.getNextGeneratedNumericSku();
        for (ProductVariantRow row : variantRows) {
            if (row.getVariantId() == null || row.getSku() == null || row.getSku().trim().isEmpty()) {
                row.setAutoSku(String.valueOf(nextSku++));
            } else {
                row.setSkuReadOnly();
            }
        }
    }

    @FXML
    private void handleSave() {
        try {
            validateInputs();

            long userId = AuthContext.getCurrentUser().id();

            List<ProductService.VariantCommand> variants = variantRows.stream().map(row -> {
                return new ProductService.VariantCommand(
                        row.getVariantId(),
                        row.getName(),
                        row.getSku(),
                        new BigDecimal(row.getPrice()),
                        new BigDecimal(row.getCostPrice()),
                        Integer.parseInt(row.getStockAlert()),
                        row.isDefault(),
                        row.getStatus(),
                        row.getStock(),
                        row.getAdjustmentReason()
                );
            }).toList();

            Long categoryId = categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null;
            Long taxId = taxFilter.getSelectedItem() != null ? taxFilter.getSelectedItem().id() : null;
            List<Long> taxIds = taxId != null ? List.of(taxId) : null;

            if (productId == null) {
                ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                        nameField.getText(),
                        descriptionField.getText(),
                        categoryId,
                        statusCombo.getValue(),
                        null,
                        variants,
                        taxIds,
                        userId
                );
                productService.createProductWithVariants(cmd);
                NotificationService.success("Product created successfully");
            } else {
                ProductService.UpdateProductCommand cmd = new ProductService.UpdateProductCommand(
                        nameField.getText(),
                        descriptionField.getText(),
                        categoryId,
                        statusCombo.getValue(),
                        null,
                        variants,
                        taxIds,
                        userId
                );
                productService.updateProduct(productId, cmd);
                NotificationService.success("Product updated successfully");
            }

            if (productSearchIndex != null) {
                productSearchIndex.refresh();
            }
            draftService.deleteDraft("product_new");
            workspaceManager.closeActiveWindow();
        } catch (Exception e) {
            NotificationService.error("Failed to save product: " + e.getMessage());
        }
    }

    private void validateInputs() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            throw new com.possum.domain.exceptions.ValidationException("Product name is required");
        }

        if (variantRows.isEmpty()) {
            throw new com.possum.domain.exceptions.ValidationException("At least one variant is required");
        }

        boolean hasDefault = false;
        for (ProductVariantRow row : variantRows) {
            if (row.getName() == null || row.getName().trim().isEmpty()) {
                throw new com.possum.domain.exceptions.ValidationException("Variant name is required");
            }
            if (row.getPrice() == null || row.getPrice().trim().isEmpty()) {
                throw new com.possum.domain.exceptions.ValidationException("Variant price is required");
            }
            if (row.getCostPrice() == null || row.getCostPrice().trim().isEmpty()) {
                throw new com.possum.domain.exceptions.ValidationException("Variant cost price is required");
            }
            if (row.getStockAlert() == null || row.getStockAlert().trim().isEmpty()) {
                throw new com.possum.domain.exceptions.ValidationException("Variant stock alert is required");
            }
            try {
                new BigDecimal(row.getPrice());
                new BigDecimal(row.getCostPrice());
            } catch (NumberFormatException e) {
                throw new com.possum.domain.exceptions.ValidationException("Price and Cost Price must be valid numbers");
            }
            try {
                Integer.parseInt(row.getStockAlert());
            } catch (NumberFormatException e) {
                throw new com.possum.domain.exceptions.ValidationException("Stock Alert must be a valid integer");
            }
            if (row.stockField.getText() == null || row.stockField.getText().trim().isEmpty()) {
                throw new com.possum.domain.exceptions.ValidationException("Current Stock is required");
            }
            try {
                Integer.parseInt(row.stockField.getText().trim());
            } catch (NumberFormatException e) {
                throw new com.possum.domain.exceptions.ValidationException("Current Stock must be a valid integer");
            }
            if (row.isDefault()) {
                hasDefault = true;
            }
        }

        if (!hasDefault) {
            throw new com.possum.domain.exceptions.ValidationException("One variant must be selected as default");
        }
    }

    @FXML
    private void handleCancel() {
        workspaceManager.closeActiveWindow();
    }

    private void replaceFieldWithLabel(Control field, String text) {
        if (field == null || field.getParent() == null) return;
        Label label = new Label(text != null ? text : "");
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #1e293b; -fx-padding: 8 12;");
        label.setWrapText(true);
        
        javafx.scene.Parent parent = field.getParent();
        if (parent instanceof VBox box) {
            int index = box.getChildren().indexOf(field);
            if (index != -1) {
                box.getChildren().set(index, label);
            }
        } else if (parent instanceof HBox box) {
            int index = box.getChildren().indexOf(field);
            if (index != -1) {
                box.getChildren().set(index, label);
            }
        }
    }

    private record CategoryItem(Long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record TaxCategoryItem(Long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown";
        }
        return status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
    }
}

package com.possum.ui.products;

import com.possum.application.auth.AuthContext;
import com.possum.application.categories.CategoryService;
import com.possum.application.products.ProductService;
import com.possum.domain.model.Category;
import com.possum.domain.model.Product;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductFormController implements Parameterizable {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final WorkspaceManager workspaceManager;
    private final SettingsStore settingsStore;
    private final ProductSearchIndex productSearchIndex;
    private final com.possum.application.drafts.DraftService draftService;

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private TextField skuField;
    @FXML private TextField barcodeField;
    @FXML private TextField priceField;
    @FXML private TextField costPriceField;
    @FXML private TextField stockAlertField;
    @FXML private TextField stockField;
    @FXML private VBox adjustmentReasonBox;
    @FXML private ComboBox<String> adjustmentReasonCombo;
    @FXML private SingleSelectFilter<CategoryItem> categoryFilter;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Button saveButton;

    private Long productId = null;
    private int initialStock = 0;

    public ProductFormController(ProductService productService,
                                 CategoryService categoryService,
                                 WorkspaceManager workspaceManager,
                                 SettingsStore settingsStore,
                                 ProductSearchIndex productSearchIndex,
                                 com.possum.application.drafts.DraftService draftService) {
        this.productService = productService;
        this.categoryService = categoryService;
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
            recoverDraft();
            skuField.setEditable(false);
            if (skuField.getText() == null || skuField.getText().isEmpty()) {
                skuField.setText(String.valueOf(productService.getNextGeneratedNumericSku()));
            }
        }
    }

    private void recoverDraft() {
        if (draftService == null) return;
        draftService.recoverDraft("product_new", ProductService.CreateProductCommand.class).ifPresent(draft -> {
            nameField.setText(draft.name() != null ? draft.name() : "");
            descriptionField.setText(draft.description() != null ? draft.description() : "");
            skuField.setText(draft.sku() != null ? draft.sku() : "");
            priceField.setText(draft.mrp() != null ? draft.mrp().toString() : "");
            costPriceField.setText(draft.costPrice() != null ? draft.costPrice().toString() : "");
            stockAlertField.setText(draft.stockAlertCap() != null ? draft.stockAlertCap().toString() : "10");
            stockField.setText(draft.initialStock() != null ? draft.initialStock().toString() : "0");
            
            if (draft.categoryId() != null) {
                categoryService.findCategoryById(draft.categoryId()).ifPresent(c -> 
                    categoryFilter.setSelectedItem(new CategoryItem(c.id(), c.name())));
            }
            statusCombo.setValue(draft.status() != null ? com.possum.shared.util.TextFormatter.toTitleCase(draft.status()) : "Active");
            
            NotificationService.success("Unsaved product draft restored.");
        });
    }

    private void loadProductDetails(boolean isView) {
        try {
            Product p = productService.getProductById(productId);

            nameField.setText(p.name());
            descriptionField.setText(p.description());
            skuField.setText(p.sku());
            priceField.setText(p.mrp() != null ? p.mrp().toString() : "");
            costPriceField.setText(p.costPrice() != null ? p.costPrice().toString() : "");
            stockAlertField.setText(p.stockAlertCap() != null ? p.stockAlertCap().toString() : "10");
            
            initialStock = p.stock() != null ? p.stock() : 0;
            stockField.setText(String.valueOf(initialStock));

            if (p.categoryId() != null) {
                categoryFilter.setSelectedItem(new CategoryItem(p.categoryId(), p.categoryName()));
            }

            statusCombo.setValue(p.status() != null ? com.possum.shared.util.TextFormatter.toTitleCase(p.status()) : "Active");

            if (isView) {
                setAllFieldsReadOnly();
                saveButton.setVisible(false);
                saveButton.setManaged(false);
            }

        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to load product details: {}", e.getMessage(), e);
            NotificationService.error("Failed to load product details: " + ErrorHandler.toUserMessage(e));
        }
    }

    private void setAllFieldsReadOnly() {
        nameField.setEditable(false);
        descriptionField.setEditable(false);
        skuField.setEditable(false);
        priceField.setEditable(false);
        costPriceField.setEditable(false);
        stockAlertField.setEditable(false);
        stockField.setEditable(false);
        categoryFilter.setDisable(true);
        statusCombo.setDisable(true);
    }

    @FXML
    public void initialize() {
        skuField.setEditable(false);
        loadCategories();

        statusCombo.setItems(FXCollections.observableArrayList("Active", "Inactive", "Discontinued"));
        adjustmentReasonCombo.setItems(FXCollections.observableArrayList("Correction", "Damage", "Return", "Stocktake", "Expiry", "Theft", "Other"));
        adjustmentReasonCombo.setValue("Correction");

        stockField.textProperty().addListener((obs, oldV, newV) -> {
            if (productId != null) {
                try {
                    int current = Integer.parseInt(newV.trim());
                    boolean changed = current != initialStock;
                    adjustmentReasonBox.setVisible(changed);
                    adjustmentReasonBox.setManaged(changed);
                } catch (Exception e) {
                    adjustmentReasonBox.setVisible(false);
                    adjustmentReasonBox.setManaged(false);
                }
            }
        });

        setupDrafting();
    }

    private void setupDrafting() {
        if (productId != null) return;
        
        nameField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        skuField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        priceField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
    }

    private void saveCurrentDraft() {
        if (productId != null || draftService == null) return;
        
        long userId = AuthContext.getCurrentUser().id();
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
            nameField.getText(), descriptionField.getText(), 
            categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
            skuField.getText(),
            parseSafeBigDecimal(priceField.getText()),
            parseSafeBigDecimal(costPriceField.getText()),
            parseSafeInt(stockAlertField.getText()),
            statusCombo.getValue() != null ? statusCombo.getValue().toLowerCase() : "active",
            null,
            parseSafeInt(stockField.getText()),
            userId
        );

        draftService.saveDraft("product_new", "product", cmd, userId);
    }

    private BigDecimal parseSafeBigDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        try { 
            return new BigDecimal(val.trim()); 
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Integer parseSafeInt(String val) {
        if (val == null || val.isBlank()) return 0;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
    }

    private void loadCategories() {
        List<Category> categories = categoryService.getAllCategories();
        categoryFilter.setItems(categories.stream().map(c -> new CategoryItem(c.id(), c.name())).toList());
    }



    @FXML
    private void handleSave() {
        try {
            validateInputs();
            long userId = AuthContext.getCurrentUser().id();

            if (productId == null) {
                ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                        nameField.getText(), descriptionField.getText(),
                        categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
                        skuField.getText(),
                        new BigDecimal(priceField.getText().trim()),
                        new BigDecimal(costPriceField.getText().trim()),
                        Integer.parseInt(stockAlertField.getText().trim()),
                        statusCombo.getValue() != null ? statusCombo.getValue().toLowerCase() : "active",
                        null,
                        Integer.parseInt(stockField.getText().trim()),
                        userId
                );
                productService.createProduct(cmd);
                NotificationService.success("Product created successfully");
            } else {
                ProductService.UpdateProductCommand cmd = new ProductService.UpdateProductCommand(
                        nameField.getText(), descriptionField.getText(),
                        categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
                        skuField.getText(),
                        new BigDecimal(priceField.getText().trim()),
                        new BigDecimal(costPriceField.getText().trim()),
                        Integer.parseInt(stockAlertField.getText().trim()),
                        statusCombo.getValue() != null ? statusCombo.getValue().toLowerCase() : "active",
                        null,
                        Integer.parseInt(stockField.getText().trim()),
                        adjustmentReasonCombo.getValue().toLowerCase(),
                        userId
                );
                productService.updateProduct(productId, cmd);
                NotificationService.success("Product updated successfully");
            }

            if (productSearchIndex != null) productSearchIndex.refresh();
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
        if (priceField.getText() == null || priceField.getText().trim().isEmpty()) {
            throw new com.possum.domain.exceptions.ValidationException("Selling price is required");
        }
        if (costPriceField.getText() == null || costPriceField.getText().trim().isEmpty()) {
            throw new com.possum.domain.exceptions.ValidationException("Cost price is required");
        }
    }

    @FXML
    private void handleCancel() {
        workspaceManager.closeActiveWindow();
    }

    private record CategoryItem(Long id, String name) {
        @Override public String toString() { return name; }
    }

}

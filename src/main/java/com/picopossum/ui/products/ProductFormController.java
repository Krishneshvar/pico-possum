package com.picopossum.ui.products;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.categories.CategoryService;
import com.picopossum.application.products.ProductService;
import com.picopossum.domain.model.Category;
import com.picopossum.domain.model.Product;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.ui.common.controls.SingleSelectFilter;
import com.picopossum.ui.common.controllers.AbstractFormController;
import com.picopossum.ui.sales.ProductSearchIndex;
import com.picopossum.ui.workspace.WorkspaceManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

public class ProductFormController extends AbstractFormController<Product> {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SettingsStore settingsStore;
    private final ProductSearchIndex productSearchIndex;
    private final com.picopossum.application.drafts.DraftService draftService;

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

    private int initialStockCount = 0;

    public ProductFormController(ProductService productService,
                                 CategoryService categoryService,
                                 WorkspaceManager workspaceManager,
                                 SettingsStore settingsStore,
                                 ProductSearchIndex productSearchIndex,
                                 com.picopossum.application.drafts.DraftService draftService) {
        super(workspaceManager);
        this.productService = productService;
        this.categoryService = categoryService;
        this.settingsStore = settingsStore;
        this.productSearchIndex = productSearchIndex;
        this.draftService = draftService;
    }

    @FXML
    public void initialize() {
        loadCategories();

        statusCombo.setItems(FXCollections.observableArrayList("Active", "Inactive", "Discontinued"));
        adjustmentReasonCombo.setItems(FXCollections.observableArrayList("Correction", "Damage", "Spoilage", "Return", "Theft"));
        adjustmentReasonCombo.setValue("Correction");

        stockField.textProperty().addListener((obs, oldV, newV) -> {
            if (entityId != null && !isViewMode()) {
                try {
                    int current = Integer.parseInt(newV.trim());
                    boolean changed = current != initialStockCount;
                    adjustmentReasonBox.setVisible(changed);
                    adjustmentReasonBox.setManaged(changed);
                } catch (Exception e) {
                    adjustmentReasonBox.setVisible(false);
                    adjustmentReasonBox.setManaged(false);
                }
            }
        });

        if (isCreateMode()) {
            recoverDraft();
            setupDrafting();
            skuField.setEditable(false);
            if (skuField.getText() == null || skuField.getText().isEmpty()) {
                skuField.setText(String.valueOf(productService.getNextGeneratedNumericSku()));
            }
        }
    }

    @Override
    protected String getEntityIdParamName() {
        return "productId";
    }

    @Override
    protected String getEntityDisplayName() {
        return "Product";
    }

    @Override
    protected Product loadEntity(Long id) {
        return productService.getProductById(id);
    }

    @Override
    protected void populateFields(Product p) {
        nameField.setText(p.name());
        descriptionField.setText(p.description());
        skuField.setText(p.sku());
        // Handling barcode specifically if it's stored in a way not reflected in basic p.sku()
        // For now using sku as primary identifier
        priceField.setText(p.mrp() != null ? p.mrp().toString() : "");
        costPriceField.setText(p.costPrice() != null ? p.costPrice().toString() : "");
        stockAlertField.setText(p.stockAlertCap() != null ? p.stockAlertCap().toString() : "10");
        
        initialStockCount = p.stock() != null ? p.stock() : 0;
        stockField.setText(String.valueOf(initialStockCount));

        if (p.categoryId() != null) {
            categoryFilter.setSelectedItem(new CategoryItem(p.categoryId(), p.categoryName()));
        }

        statusCombo.setValue(p.status() != null ? com.picopossum.shared.util.TextFormatter.toTitleCase(p.status()) : "Active");
    }

    @Override
    protected void setupValidators() {
        // Handled by common validation if desired, otherwise AbstractFormController.validateForm() handles it
    }

    @Override
    protected void setFormEditable(boolean editable) {
        if (!editable) {
            replaceWithLabel(nameField);
            replaceWithLabel(descriptionField);
            replaceWithLabel(skuField);
            replaceWithLabel(barcodeField);
            replaceWithLabel(priceField);
            replaceWithLabel(costPriceField);
            replaceWithLabel(stockAlertField);
            replaceWithLabel(stockField);
            replaceWithLabel(categoryFilter);
            replaceWithLabel(statusCombo);
            
            adjustmentReasonBox.setVisible(false);
            adjustmentReasonBox.setManaged(false);
        } else {
            nameField.setEditable(true);
            descriptionField.setEditable(true);
            skuField.setEditable(false);
            barcodeField.setEditable(true);
            priceField.setEditable(true);
            costPriceField.setEditable(true);
            stockAlertField.setEditable(true);
            stockField.setEditable(true);
            categoryFilter.setDisable(false);
            statusCombo.setDisable(false);
        }
    }

    @Override
    protected void createEntity() throws Exception {
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
                nameField.getText(), descriptionField.getText(),
                categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
                skuField.getText(),
                new BigDecimal(priceField.getText().trim()),
                new BigDecimal(costPriceField.getText().trim()),
                Integer.parseInt(stockAlertField.getText().trim()),
                statusCombo.getValue() != null ? statusCombo.getValue().toLowerCase() : "active",
                null,
                Integer.parseInt(stockField.getText().trim())
        );
        productService.createProduct(cmd);
        refreshIndex();
    }

    @Override
    protected void updateEntity() throws Exception {
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
                adjustmentReasonCombo.getValue().toLowerCase()
        );
        productService.updateProduct(entityId, cmd);
        refreshIndex();
    }

    private void refreshIndex() {
        if (productSearchIndex != null) productSearchIndex.refresh();
        draftService.deleteDraft("product_new");
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
            statusCombo.setValue(draft.status() != null ? com.picopossum.shared.util.TextFormatter.toTitleCase(draft.status()) : "Active");
            
            NotificationService.success("Unsaved product draft restored.");
        });
    }

    private void setupDrafting() {
        nameField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        skuField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
        priceField.textProperty().addListener((o, old, newVal) -> saveCurrentDraft());
    }

    private void saveCurrentDraft() {
        if (entityId != null || draftService == null) return;
        
        ProductService.CreateProductCommand cmd = new ProductService.CreateProductCommand(
            nameField.getText(), descriptionField.getText(), 
            categoryFilter.getSelectedItem() != null ? categoryFilter.getSelectedItem().id() : null,
            skuField.getText(),
            parseSafeBigDecimal(priceField.getText()),
            parseSafeBigDecimal(costPriceField.getText()),
            parseSafeInt(stockAlertField.getText()),
            statusCombo.getValue() != null ? statusCombo.getValue().toLowerCase() : "active",
            null,
            parseSafeInt(stockField.getText())
        );

        draftService.saveDraft("product_new", "product", cmd, 0L); // 0L as placeholder for single-user
    }

    private BigDecimal parseSafeBigDecimal(String val) {
        if (val == null || val.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(val.trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Integer parseSafeInt(String val) {
        if (val == null || val.isBlank()) return 0;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
    }

    private void loadCategories() {
        List<Category> categories = categoryService.getAllCategories();
        categoryFilter.setItems(categories.stream().map(c -> new CategoryItem(c.id(), c.name())).toList());
    }

    private record CategoryItem(Long id, String name) {
        @Override public String toString() { return name; }
    }
}

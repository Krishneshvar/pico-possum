package com.possum.ui.products;

import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Encapsulates the UI row for a single product variant in the Product Form.
 */
public class ProductVariantRow {
    private final VBox view;
    final TextField variantNameField;
    final TextField skuField;
    final TextField priceField;
    final TextField costPriceField;
    final TextField marginField;
    final TextField stockAlertField;
    public final TextField stockField; // accessed by validator in controller
    final ComboBox<String> adjustmentReasonCombo;
    final VBox adjustmentReasonBox;
    final ComboBox<String> variantStatusCombo;
    final RadioButton defaultRadio;
    private Long variantId = null;
    private Integer initialStock = 0;

    private final Button removeBtn;
    private final Consumer<ProductVariantRow> onRemove;

    public ProductVariantRow(boolean isDefault, String initialName, ToggleGroup defaultVariantGroup, Consumer<ProductVariantRow> onRemove) {
        this.onRemove = onRemove;
        
        view = new VBox(14);
        view.getStyleClass().add("variant-row-container");

        HBox headerBox = new HBox(12);
        headerBox.getStyleClass().add("variant-row-header");
        headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label title = new Label("Variant Details");
        title.getStyleClass().add("variant-row-title");

        defaultRadio = new RadioButton("Default Variant");
        defaultRadio.setToggleGroup(defaultVariantGroup);
        defaultRadio.setSelected(isDefault);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        removeBtn = new Button("Remove");
        FontIcon trashIcon = new FontIcon("bx-trash");
        trashIcon.setIconSize(14);
        trashIcon.setIconColor(javafx.scene.paint.Color.web("#ef4444"));
        removeBtn.setGraphic(trashIcon);
        removeBtn.getStyleClass().add("btn-remove-variant");
        removeBtn.setOnAction(e -> this.onRemove.accept(this));

        headerBox.getChildren().addAll(title, defaultRadio, spacer, removeBtn);

        HBox row1 = new HBox(14);
        row1.getStyleClass().add("variant-field-row");
        variantNameField = createTextField("Variant Name", initialName != null ? initialName : "e.g. Small, Red");
        skuField = createTextField("SKU", "Stock Keeping Unit");
        row1.getChildren().addAll(createFieldBox("Name *", variantNameField), createFieldBox("SKU", skuField));

        HBox row2 = new HBox(14);
        row2.getStyleClass().add("variant-field-row");
        priceField = createTextField("Price", "0.00");
        priceField.setTextFormatter(com.possum.ui.common.controls.InputFilters.decimalFormat());
        costPriceField = createTextField("Cost Price", "0.00");
        costPriceField.setTextFormatter(com.possum.ui.common.controls.InputFilters.decimalFormat());
        marginField = createTextField("Margin %", "0.00%");
        marginField.setEditable(false);
        marginField.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #64748b;");

        priceField.textProperty().addListener((obs, oldV, newV) -> updateMargin());
        costPriceField.textProperty().addListener((obs, oldV, newV) -> updateMargin());

        row2.getChildren().addAll(
                createFieldBox("Price *", priceField),
                createFieldBox("Cost Price *", costPriceField),
                createFieldBox("Margin %", marginField)
        );

        com.possum.ui.common.validation.FieldValidator.of(priceField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Price"))
                .custom(val -> {
                    try { 
                        String clean = val.replace(com.possum.shared.util.CurrencyUtil.getSymbol(), "").replace(",", "").trim();
                        return new BigDecimal(clean).compareTo(BigDecimal.ZERO) >= 0; 
                    } catch (Exception e) { return false; }
                }, "Must be non-negative number")
                .validateOnType();

        com.possum.ui.common.validation.FieldValidator.of(costPriceField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Cost Price"))
                .custom(val -> {
                    try { 
                        String clean = val.replace(com.possum.shared.util.CurrencyUtil.getSymbol(), "").replace(",", "").trim();
                        return new BigDecimal(clean).compareTo(BigDecimal.ZERO) >= 0; 
                    } catch (Exception e) { return false; }
                }, "Must be non-negative number")
                .validateOnType();

        com.possum.ui.common.validation.FieldValidator.of(variantNameField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Variant Name"))
                .validateOnType();

        HBox row3 = new HBox(14);
        row3.getStyleClass().add("variant-field-row");
        stockAlertField = createTextField("Stock Alert Cap", "10");
        stockAlertField.setTextFormatter(com.possum.ui.common.controls.InputFilters.numericFormat());
        variantStatusCombo = new ComboBox<>(FXCollections.observableArrayList("active", "inactive", "discontinued"));
        variantStatusCombo.setConverter(new javafx.util.StringConverter<String>() {
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
        variantStatusCombo.setValue("active");
        variantStatusCombo.setMaxWidth(Double.MAX_VALUE);

        stockField = createTextField("Stock Level", "0");
        stockField.setTextFormatter(com.possum.ui.common.controls.InputFilters.numericFormat());
        stockField.textProperty().addListener((obs, oldV, newV) -> updateAdjustmentReasonVisibility());

        adjustmentReasonCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Correction", "Damage", "Return", "Stocktake", "Expiry", "Theft", "Other"
        ));
        adjustmentReasonCombo.setValue("Correction");
        adjustmentReasonCombo.setMaxWidth(Double.MAX_VALUE);

        adjustmentReasonBox = createFieldBox("Adjustment Reason *", adjustmentReasonCombo);
        adjustmentReasonBox.setVisible(false);
        adjustmentReasonBox.setManaged(false);

        row3.getChildren().addAll(
                createFieldBox("Stock Alert", stockAlertField),
                createFieldBox("Status", variantStatusCombo),
                createFieldBox("Current Stock", stockField)
        );

        view.getChildren().addAll(headerBox, row1, row2, row3, adjustmentReasonBox);

        com.possum.ui.common.validation.FieldValidator.of(stockAlertField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Stock Alert"))
                .custom(val -> {
                    try { Integer.parseInt(val); return true; } catch (Exception e) { return false; }
                }, "Must be an integer")
                .validateOnType();

        com.possum.ui.common.validation.FieldValidator.of(stockField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Stock Level"))
                .custom(val -> {
                    try { Integer.parseInt(val); return true; } catch (Exception e) { return false; }
                }, "Must be an integer")
                .validateOnType();

        updateMargin();
    }

    public void updateAvailableStatuses(boolean productIsActive) {
        String currentStatus = variantStatusCombo.getValue();
        if (productIsActive) {
            variantStatusCombo.setItems(FXCollections.observableArrayList("active", "inactive", "discontinued"));
        } else {
            variantStatusCombo.setItems(FXCollections.observableArrayList("inactive", "discontinued"));
            if ("active".equals(currentStatus)) {
                variantStatusCombo.setValue("inactive");
            }
        }
    }

    private void updateAdjustmentReasonVisibility() {
        if (variantId == null) return;

        try {
            int currentStock = Integer.parseInt(stockField.getText().trim());
            boolean changed = currentStock != initialStock;
            adjustmentReasonBox.setVisible(changed);
            adjustmentReasonBox.setManaged(changed);
        } catch (NumberFormatException e) {
            adjustmentReasonBox.setVisible(false);
            adjustmentReasonBox.setManaged(false);
        }
    }

    public void setInitialStock(Integer stock) {
        this.initialStock = stock;
        this.stockField.setText(stock.toString());
    }

    private void updateMargin() {
        try {
            String priceStr = priceField.getText().trim();
            String costStr = costPriceField.getText().trim();

            if (priceStr.isEmpty() || costStr.isEmpty()) {
                marginField.setText("0.00%");
                return;
            }

            BigDecimal price = new BigDecimal(priceStr);
            BigDecimal cost = new BigDecimal(costStr);

            if (price.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = price.subtract(cost)
                        .divide(price, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                marginField.setText(String.format("%.2f%%", margin));
            } else {
                marginField.setText("0.00%");
            }
        } catch (Exception e) {
            marginField.setText("0.00%");
        }
    }

    public void setReadOnly() {
        replaceFieldWithLabel(variantNameField, variantNameField.getText());
        replaceFieldWithLabel(skuField, skuField.getText());
        replaceFieldWithLabel(priceField, priceField.getText());
        replaceFieldWithLabel(costPriceField, costPriceField.getText());
        replaceFieldWithLabel(marginField, marginField.getText());
        replaceFieldWithLabel(stockAlertField, stockAlertField.getText());
        replaceFieldWithLabel(variantStatusCombo, variantStatusCombo.getValue());
        replaceFieldWithLabel(stockField, stockField.getText());

        if (adjustmentReasonBox.isVisible()) {
            replaceFieldWithLabel(adjustmentReasonCombo, adjustmentReasonCombo.getValue());
        }

        defaultRadio.setDisable(true);
        defaultRadio.setStyle("-fx-opacity: 1; -fx-text-fill: black;");

        removeBtn.setVisible(false);
        removeBtn.setManaged(false);

        if (isDefault()) {
            view.setStyle("-fx-border-color: #3b82f6; -fx-border-width: 2; -fx-border-radius: 6; -fx-padding: 15; -fx-background-color: #eff6ff;");
        }
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

    private TextField createTextField(String prompt, String text) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        if (text != null && !text.isEmpty() && !text.startsWith("e.g.") && !text.equals("Stock Keeping Unit")) {
            field.setText(text);
        }
        return field;
    }

    private VBox createFieldBox(String labelText, Control field) {
        VBox box = new VBox(6);
        box.getStyleClass().add("variant-field-box");
        Label label = new Label(labelText);
        label.getStyleClass().add("variant-field-label");
        box.getChildren().addAll(label, field);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    public VBox getView() {
        return view;
    }

    public String getName() { return variantNameField.getText(); }
    public String getSku() { return skuField.getText(); }
    public String getPrice() { return priceField.getText(); }
    public String getCostPrice() { return costPriceField.getText(); }
    public String getStockAlert() { return stockAlertField.getText(); }
    public String getStatus() { return variantStatusCombo.getValue(); }
    public Integer getStock() {
        try {
            return Integer.parseInt(stockField.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    public String getAdjustmentReason() {
        return adjustmentReasonBox.isVisible() ? adjustmentReasonCombo.getValue().toLowerCase() : null;
    }
    public boolean isDefault() { return defaultRadio.isSelected(); }
    public void setDefault(boolean isDefault) { defaultRadio.setSelected(isDefault); }

    public void setVariantId(Long id) { this.variantId = id; }
    public Long getVariantId() { return variantId; }

    public void setSkuReadOnly() {
        skuField.setDisable(true);
        skuField.setEditable(false);
    }

    public void setSkuEditable() {
        skuField.setDisable(false);
        skuField.setEditable(true);
    }

    public void setAutoSku(String skuValue) {
        skuField.setText(skuValue != null ? skuValue : "");
        setSkuReadOnly();
    }
    
    public void setVariantName(String name) { variantNameField.setText(name); }
    public void setSku(String sku) { skuField.setText(sku); }
    public void setPrice(String price) { priceField.setText(price); }
    public void setCostPrice(String cost) { costPriceField.setText(cost); }
    public void setStockAlert(String alert) { stockAlertField.setText(alert); }
    public void setVariantStatus(String status) { variantStatusCombo.setValue(status); }
}

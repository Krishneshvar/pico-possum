package com.possum.ui.settings.tax;

import com.possum.application.taxes.TaxManagementService;
import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.DialogStyler;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TaxRulesController {

    @FXML private ComboBox<TaxProfile> profileCombo;
    @FXML private DataTableView<TaxRule> rulesTable;
    private TableColumn<TaxRule, String> categoryColumn;
    private TableColumn<TaxRule, BigDecimal> rateColumn;
    private TableColumn<TaxRule, String> typeColumn;
    private TableColumn<TaxRule, Integer> priorityColumn;

    @FXML private ComboBox<TaxCategory> categoryCombo;
    @FXML private ComboBox<String> scopeCombo;
    @FXML private TextField rateField;
    @FXML private TextField priorityField;
    @FXML private CheckBox compoundCheckBox;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private TextField minInvoiceField;
    @FXML private TextField maxInvoiceField;
    @FXML private TextField customerTypeField;
    @FXML private DatePicker validFromPicker;
    @FXML private DatePicker validToPicker;

    @FXML private Label profileErrorLabel;
    @FXML private Label scopeErrorLabel;
    @FXML private Label rateErrorLabel;
    @FXML private Label priorityErrorLabel;
    @FXML private Label minPriceErrorLabel;
    @FXML private Label maxPriceErrorLabel;
    @FXML private Label minInvoiceErrorLabel;
    @FXML private Label maxInvoiceErrorLabel;
    @FXML private Label dateRangeErrorLabel;

    private TaxManagementService taxService;
    private TaxRule selectedRule;

    public void setTaxService(TaxManagementService taxService) {
        this.taxService = taxService;
        loadProfiles();
        loadCategories();
    }

    @FXML
    public void initialize() {
        setupTable();
        setupForm();
    }

    private void setupTable() {
        categoryColumn = new TableColumn<>("Category");
        rateColumn = new TableColumn<>("Rate (%)");
        typeColumn = new TableColumn<>("Scope");
        priorityColumn = new TableColumn<>("Priority");

        rulesTable.getTableView().getColumns().clear();
        rulesTable.getTableView().getColumns().addAll(List.of(categoryColumn, rateColumn, typeColumn, priorityColumn));
        rulesTable.setEmptyMessage("No tax rules found for this profile");
        rulesTable.setEmptySubtitle("Add a new rule using the form.");

        categoryColumn.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().categoryName() != null ? data.getValue().categoryName() : "All Categories"));
        rateColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().ratePercent()));
        typeColumn.setCellValueFactory(data ->
            new SimpleStringProperty(Boolean.TRUE.equals(data.getValue().compound()) ? "Compound" : "Simple"));
        priorityColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().priority()));

        rulesTable.getTableView().getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectedRule = newVal;
            if (newVal != null) {
                populateForm(newVal);
            }
        });
    }

    private void setupForm() {
        scopeCombo.setItems(FXCollections.observableArrayList("ITEM", "INVOICE"));
        scopeCombo.setValue("ITEM");
        priorityField.setText("0");

        profileCombo.setOnAction(e -> {
            clearFieldError(profileCombo, profileErrorLabel);
            loadRulesForSelectedProfile();
        });

        scopeCombo.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateScope();
            }
        });
        rateField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateRequiredDecimal(rateField, rateErrorLabel, "Tax rate is required");
            }
        });
        priorityField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateInteger(priorityField, priorityErrorLabel, "Priority must be a whole number");
            }
        });
        minPriceField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateOptionalDecimal(minPriceField, minPriceErrorLabel, "Invalid min item price");
                validateRange(minPriceField, maxPriceField, minPriceErrorLabel, maxPriceErrorLabel, "Min Item Price should be <= Max Item Price");
            }
        });
        maxPriceField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateOptionalDecimal(maxPriceField, maxPriceErrorLabel, "Invalid max item price");
                validateRange(minPriceField, maxPriceField, minPriceErrorLabel, maxPriceErrorLabel, "Min Item Price should be <= Max Item Price");
            }
        });
        minInvoiceField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateOptionalDecimal(minInvoiceField, minInvoiceErrorLabel, "Invalid min invoice total");
                validateRange(minInvoiceField, maxInvoiceField, minInvoiceErrorLabel, maxInvoiceErrorLabel, "Min Invoice Total should be <= Max Invoice Total");
            }
        });
        maxInvoiceField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateOptionalDecimal(maxInvoiceField, maxInvoiceErrorLabel, "Invalid max invoice total");
                validateRange(minInvoiceField, maxInvoiceField, minInvoiceErrorLabel, maxInvoiceErrorLabel, "Min Invoice Total should be <= Max Invoice Total");
            }
        });

        validFromPicker.valueProperty().addListener((obs, old, val) -> validateDateRange());
        validToPicker.valueProperty().addListener((obs, old, val) -> validateDateRange());
    }

    private void loadProfiles() {
        if (taxService != null) {
            List<TaxProfile> profiles = taxService.getAllTaxProfiles();
            profileCombo.setItems(FXCollections.observableArrayList(profiles));
            profileCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(TaxProfile item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name() + (Boolean.TRUE.equals(item.active()) ? " (Active)" : ""));
                }
            });
            profileCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(TaxProfile item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            });

            if (!profiles.isEmpty()) {
                profileCombo.setValue(profiles.get(0));
                loadRulesForSelectedProfile();
            }
        }
    }

    private void loadCategories() {
        if (taxService != null) {
            List<TaxCategory> categories = taxService.getAllTaxCategories();
            categoryCombo.setItems(FXCollections.observableArrayList(categories));
            categoryCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(TaxCategory item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "All Categories" : item.name());
                }
            });
            categoryCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(TaxCategory item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "All Categories" : item.name());
                }
            });
        }
    }

    private void loadRulesForSelectedProfile() {
        TaxProfile profile = profileCombo.getValue();
        if (profile != null && taxService != null) {
            rulesTable.setItems(FXCollections.observableArrayList(
                taxService.getTaxRulesByProfileId(profile.id())
            ));
        }
    }

    private void populateForm(TaxRule rule) {
        // Find matching category in combo or set null for "All Categories"
        if (rule.taxCategoryId() == null) {
            categoryCombo.setValue(null);
        } else {
            categoryCombo.getItems().stream()
                .filter(c -> c != null && c.id().equals(rule.taxCategoryId()))
                .findFirst()
                .ifPresent(categoryCombo::setValue);
        }

        scopeCombo.setValue(rule.ruleScope());
        rateField.setText(rule.ratePercent().toString());
        priorityField.setText(rule.priority().toString());
        compoundCheckBox.setSelected(Boolean.TRUE.equals(rule.compound()));
        minPriceField.setText(rule.minPrice() != null ? rule.minPrice().toString() : "");
        maxPriceField.setText(rule.maxPrice() != null ? rule.maxPrice().toString() : "");
        minInvoiceField.setText(rule.minInvoiceTotal() != null ? rule.minInvoiceTotal().toString() : "");
        maxInvoiceField.setText(rule.maxInvoiceTotal() != null ? rule.maxInvoiceTotal().toString() : "");
        customerTypeField.setText(rule.customerType() != null ? rule.customerType() : "");
        validFromPicker.setValue(rule.validFrom());
        validToPicker.setValue(rule.validTo());

        clearValidationState();
    }

    private void clearForm() {
        categoryCombo.setValue(null);
        scopeCombo.setValue("ITEM");
        rateField.clear();
        priorityField.setText("0");
        compoundCheckBox.setSelected(false);
        minPriceField.clear();
        maxPriceField.clear();
        minInvoiceField.clear();
        maxInvoiceField.clear();
        customerTypeField.clear();
        validFromPicker.setValue(null);
        validToPicker.setValue(null);
        selectedRule = null;
        rulesTable.getTableView().getSelectionModel().clearSelection();

        clearValidationState();
    }

    @FXML
    private void handleAdd() {
        TaxProfile profile = profileCombo.getValue();
        if (profile == null) {
            showFieldError(profileCombo, profileErrorLabel, "Select a tax profile");
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        if (!validateRuleForm()) {
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        try {
            TaxCategory category = categoryCombo.getValue();
            TaxRule rule = new TaxRule(
                null,
                profile.id(),
                category != null ? category.id() : null,
                scopeCombo.getValue(),
                parseDecimal(minPriceField.getText()),
                parseDecimal(maxPriceField.getText()),
                parseDecimal(minInvoiceField.getText()),
                parseDecimal(maxInvoiceField.getText()),
                customerTypeField.getText().trim().isEmpty() ? null : customerTypeField.getText().trim(),
                new BigDecimal(rateField.getText().trim()),
                compoundCheckBox.isSelected(),
                Integer.parseInt(priorityField.getText().trim()),
                validFromPicker.getValue(),
                validToPicker.getValue(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
            );

            taxService.createTaxRule(rule);
            NotificationService.success("Tax rule created");
            loadRulesForSelectedProfile();
            clearForm();
        } catch (NumberFormatException e) {
            NotificationService.error("Invalid number format");
        } catch (Exception e) {
            NotificationService.error("Failed to create rule: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedRule == null) {
            NotificationService.warning("Select a rule to update");
            return;
        }

        if (!validateRuleForm()) {
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        try {
            TaxCategory category = categoryCombo.getValue();
            TaxRule rule = new TaxRule(
                selectedRule.id(),
                selectedRule.taxProfileId(),
                category != null ? category.id() : null,
                scopeCombo.getValue(),
                parseDecimal(minPriceField.getText()),
                parseDecimal(maxPriceField.getText()),
                parseDecimal(minInvoiceField.getText()),
                parseDecimal(maxInvoiceField.getText()),
                customerTypeField.getText().trim().isEmpty() ? null : customerTypeField.getText().trim(),
                new BigDecimal(rateField.getText().trim()),
                compoundCheckBox.isSelected(),
                Integer.parseInt(priorityField.getText().trim()),
                validFromPicker.getValue(),
                validToPicker.getValue(),
                null,
                null,
                null
            );

            taxService.updateTaxRule(selectedRule.id(), rule);
            NotificationService.success("Tax rule updated");
            loadRulesForSelectedProfile();
            clearForm();
        } catch (NumberFormatException e) {
            NotificationService.error("Invalid number format");
        } catch (Exception e) {
            NotificationService.error("Failed to update rule: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedRule == null) {
            NotificationService.warning("Select a rule to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);

        DialogStyler.apply(confirm);
        confirm.setTitle("Delete Tax Rule");
        confirm.setHeaderText("Delete this tax rule?");
        confirm.setContentText("This action cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    taxService.deleteTaxRule(selectedRule.id());
                    NotificationService.success("Tax rule deleted");
                    loadRulesForSelectedProfile();
                    clearForm();
                } catch (Exception e) {
                    NotificationService.error("Failed to delete rule: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    private boolean validateRuleForm() {
        boolean valid = true;

        TaxProfile profile = profileCombo.getValue();
        if (profile == null) {
            showFieldError(profileCombo, profileErrorLabel, "Select a tax profile");
            valid = false;
        } else {
            clearFieldError(profileCombo, profileErrorLabel);
        }

        valid &= validateScope();
        valid &= validateRequiredDecimal(rateField, rateErrorLabel, "Tax rate is required");
        valid &= validateInteger(priorityField, priorityErrorLabel, "Priority must be a whole number");

        valid &= validateOptionalDecimal(minPriceField, minPriceErrorLabel, "Invalid min item price");
        valid &= validateOptionalDecimal(maxPriceField, maxPriceErrorLabel, "Invalid max item price");
        valid &= validateOptionalDecimal(minInvoiceField, minInvoiceErrorLabel, "Invalid min invoice total");
        valid &= validateOptionalDecimal(maxInvoiceField, maxInvoiceErrorLabel, "Invalid max invoice total");

        valid &= validateRange(minPriceField, maxPriceField, minPriceErrorLabel, maxPriceErrorLabel, "Min Item Price should be <= Max Item Price");
        valid &= validateRange(minInvoiceField, maxInvoiceField, minInvoiceErrorLabel, maxInvoiceErrorLabel, "Min Invoice Total should be <= Max Invoice Total");

        valid &= validateDateRange();

        return valid;
    }

    private boolean validateScope() {
        if (scopeCombo.getValue() == null || scopeCombo.getValue().trim().isEmpty()) {
            showFieldError(scopeCombo, scopeErrorLabel, "Tax scope is required");
            return false;
        }
        clearFieldError(scopeCombo, scopeErrorLabel);
        return true;
    }

    private boolean validateRequiredDecimal(TextField field, Label errorLabel, String requiredMessage) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            showFieldError(field, errorLabel, requiredMessage);
            return false;
        }
        try {
            new BigDecimal(text);
            clearFieldError(field, errorLabel);
            return true;
        } catch (NumberFormatException e) {
            showFieldError(field, errorLabel, "Use a valid number");
            return false;
        }
    }

    private boolean validateOptionalDecimal(TextField field, Label errorLabel, String invalidMessage) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            clearFieldError(field, errorLabel);
            return true;
        }
        try {
            new BigDecimal(text);
            clearFieldError(field, errorLabel);
            return true;
        } catch (NumberFormatException e) {
            showFieldError(field, errorLabel, invalidMessage);
            return false;
        }
    }

    private boolean validateInteger(TextField field, Label errorLabel, String invalidMessage) {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            showFieldError(field, errorLabel, invalidMessage);
            return false;
        }
        try {
            Integer.parseInt(text);
            clearFieldError(field, errorLabel);
            return true;
        } catch (NumberFormatException e) {
            showFieldError(field, errorLabel, invalidMessage);
            return false;
        }
    }

    private boolean validateRange(TextField minField, TextField maxField, Label minErrorLabel, Label maxErrorLabel, String message) {
        BigDecimal min = parseDecimal(minField.getText());
        BigDecimal max = parseDecimal(maxField.getText());
        if (min != null && max != null && min.compareTo(max) > 0) {
            showFieldError(minField, minErrorLabel, message);
            showFieldError(maxField, maxErrorLabel, message);
            return false;
        }

        if (min != null || (minField.getText() == null || minField.getText().trim().isEmpty())) {
            clearFieldError(minField, minErrorLabel);
        }
        if (max != null || (maxField.getText() == null || maxField.getText().trim().isEmpty())) {
            clearFieldError(maxField, maxErrorLabel);
        }
        return true;
    }

    private boolean validateDateRange() {
        if (validFromPicker.getValue() != null && validToPicker.getValue() != null
            && validFromPicker.getValue().isAfter(validToPicker.getValue())) {
            showFieldError(validToPicker, dateRangeErrorLabel, "Valid To date must be on or after Valid From date");
            return false;
        }
        clearFieldError(validToPicker, dateRangeErrorLabel);
        return true;
    }

    private void clearValidationState() {
        clearFieldError(profileCombo, profileErrorLabel);
        clearFieldError(scopeCombo, scopeErrorLabel);
        clearFieldError(rateField, rateErrorLabel);
        clearFieldError(priorityField, priorityErrorLabel);
        clearFieldError(minPriceField, minPriceErrorLabel);
        clearFieldError(maxPriceField, maxPriceErrorLabel);
        clearFieldError(minInvoiceField, minInvoiceErrorLabel);
        clearFieldError(maxInvoiceField, maxInvoiceErrorLabel);
        clearFieldError(validToPicker, dateRangeErrorLabel);
    }

    private BigDecimal parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void showFieldError(Control field, Label errorLabel, String message) {
        if (!field.getStyleClass().contains("input-error")) {
            field.getStyleClass().add("input-error");
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearFieldError(Control field, Label errorLabel) {
        field.getStyleClass().remove("input-error");
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}

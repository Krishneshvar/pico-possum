package com.possum.ui.settings.tax;

import com.possum.application.taxes.TaxManagementService;
import com.possum.domain.model.TaxCategory;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.DialogStyler;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.List;

public class TaxCategoriesController {

    @FXML private DataTableView<TaxCategory> categoriesTable;
    private TableColumn<TaxCategory, String> nameColumn;
    private TableColumn<TaxCategory, String> descriptionColumn;
    private TableColumn<TaxCategory, Integer> productCountColumn;

    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private Label nameErrorLabel;

    private TaxManagementService taxService;
    private TaxCategory selectedCategory;

    public void setTaxService(TaxManagementService taxService) {
        this.taxService = taxService;
        loadCategories();
    }

    @FXML
    public void initialize() {
        setupTable();
        setupValidation();
    }

    private void setupTable() {
        nameColumn = new TableColumn<>("Category Name");
        descriptionColumn = new TableColumn<>("Description");
        productCountColumn = new TableColumn<>("Products Assigned");

        categoriesTable.getTableView().getColumns().clear();
        categoriesTable.getTableView().getColumns().addAll(List.of(nameColumn, descriptionColumn, productCountColumn));
        categoriesTable.setEmptyMessage("No tax categories found");
        categoriesTable.setEmptySubtitle("Add a new category to link it with products.");

        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        descriptionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().description()));
        productCountColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().productCount()));

        categoriesTable.getTableView().getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            selectedCategory = newVal;
            if (newVal != null) {
                populateForm(newVal);
            }
        });
    }

    private void setupValidation() {
        nameField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                validateName();
            }
        });
    }

    private void loadCategories() {
        if (taxService != null) {
            categoriesTable.setItems(FXCollections.observableArrayList(taxService.getAllTaxCategories()));
        }
    }

    private void populateForm(TaxCategory category) {
        nameField.setText(category.name());
        descriptionArea.setText(category.description());
        clearFieldError(nameField, nameErrorLabel);
    }

    private void clearForm() {
        nameField.clear();
        descriptionArea.clear();
        clearFieldError(nameField, nameErrorLabel);
        selectedCategory = null;
        categoriesTable.getTableView().getSelectionModel().clearSelection();
    }

    @FXML
    private void handleAdd() {
        if (!validateName()) {
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        try {
            taxService.createTaxCategory(
                nameField.getText().trim(),
                descriptionArea.getText().trim()
            );
            NotificationService.success("Tax category created");
            loadCategories();
            clearForm();
        } catch (Exception e) {
            NotificationService.error("Failed to create category: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedCategory == null) {
            NotificationService.warning("Select a category to update");
            return;
        }

        if (!validateName()) {
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        try {
            taxService.updateTaxCategory(
                selectedCategory.id(),
                nameField.getText().trim(),
                descriptionArea.getText().trim()
            );
            NotificationService.success("Tax category updated");
            loadCategories();
            clearForm();
        } catch (Exception e) {
            NotificationService.error("Failed to update category: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedCategory == null) {
            NotificationService.warning("Select a category to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);

        DialogStyler.apply(confirm);
        confirm.setTitle("Delete Tax Category");
        confirm.setHeaderText("Delete " + selectedCategory.name() + "?");
        confirm.setContentText("This category cannot be deleted if it's used by products.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    taxService.deleteTaxCategory(selectedCategory.id());
                    NotificationService.success("Tax category deleted");
                    loadCategories();
                    clearForm();
                } catch (IllegalStateException e) {
                    NotificationService.error("Cannot delete: " + e.getMessage());
                } catch (Exception e) {
                    NotificationService.error("Failed to delete category: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    private boolean validateName() {
        String value = nameField.getText() == null ? "" : nameField.getText().trim();
        if (value.isEmpty()) {
            showFieldError(nameField, nameErrorLabel, "Category name is required");
            return false;
        }
        clearFieldError(nameField, nameErrorLabel);
        return true;
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

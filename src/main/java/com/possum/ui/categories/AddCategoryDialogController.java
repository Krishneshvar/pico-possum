package com.possum.ui.categories;

import com.possum.application.categories.CategoryService;
import com.possum.domain.model.Category;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.navigation.Parameterizable;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import com.possum.ui.common.dialogs.DialogStyler;

import java.util.List;
import java.util.Map;

public class AddCategoryDialogController implements Parameterizable {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<Category> parentCategoryComboBox;
    @FXML private Button saveButton;

    private final CategoryService categoryService;
    private final WorkspaceManager workspaceManager;
    private Category editingCategory;

    public AddCategoryDialogController(CategoryService categoryService, WorkspaceManager workspaceManager) {
        this.categoryService = categoryService;
        this.workspaceManager = workspaceManager;
    }

    @FXML
    public void initialize() {
        parentCategoryComboBox.setConverter(new StringConverter<Category>() {
            @Override
            public String toString(Category category) {
                return category == null ? "None" : category.name();
            }

            @Override
            public Category fromString(String string) {
                return null;
            }
        });

        Platform.runLater(this::loadCategories);
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params != null && params.containsKey("category")) {
            this.editingCategory = (Category) params.get("category");
            updateUIForEdit();
        }
    }

    private void updateUIForEdit() {
        if (titleLabel != null) titleLabel.setText("Edit Category");
        if (subtitleLabel != null) subtitleLabel.setText("Update existing category details");
        if (saveButton != null) {
            saveButton.setText("Update Category");
            if (saveButton.getTooltip() != null) {
                saveButton.getTooltip().setText("Save changes to the category");
            }
        }
        
        if (editingCategory != null) {
            nameField.setText(editingCategory.name());
        }
    }

    private void loadCategories() {
        List<Category> allCategories = categoryService.getAllCategories();
        // Remove the current category from the parent possibilities if editing
        if (editingCategory != null) {
            allCategories.removeIf(c -> c.id().equals(editingCategory.id()));
        }
        parentCategoryComboBox.setItems(FXCollections.observableArrayList(allCategories));

        if (editingCategory != null && editingCategory.parentId() != null) {
            allCategories.stream()
                    .filter(c -> c.id().equals(editingCategory.parentId()))
                    .findFirst()
                    .ifPresent(parentCategoryComboBox::setValue);
        }
    }

    @FXML
    private void handleSave() {
        String name = nameField.getText();
        if (name == null || name.trim().isEmpty()) {
            showAlert("Error", "Category name is required.");
            return;
        }

        Category selectedParent = parentCategoryComboBox.getValue();
        Long parentId = selectedParent != null ? selectedParent.id() : null;

        try {
            if (editingCategory == null) {
                categoryService.createCategory(name.trim(), parentId);
            } else {
                categoryService.updateCategory(editingCategory.id(), name.trim(), parentId);
            }
            nameField.getScene().getWindow().hide();
        } catch (Exception e) {
            String action = editingCategory == null ? "create" : "update";
            showAlert("Error", "Failed to " + action + " category: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        nameField.getScene().getWindow().hide();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        DialogStyler.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}

package com.picopossum.ui.categories;

import com.picopossum.application.categories.CategoryService;
import com.picopossum.domain.model.Category;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.navigation.Parameterizable;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import com.picopossum.ui.common.dialogs.DialogStyler;

import com.picopossum.ui.common.ErrorHandler;
import java.util.List;
import java.util.ArrayList;
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
            loadCategories();
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
        List<Category> allCategories = new ArrayList<>(categoryService.getAllCategories());
        
        // Remove the current category and its descendants from the parent possibilities if editing
        if (editingCategory != null && allCategories != null) {
            java.util.Set<Long> descendants = categoryService.getDescendantIds(editingCategory.id());
            allCategories.removeIf(c -> c == null || c.id() == null || c.id().equals(editingCategory.id()) || (descendants != null && descendants.contains(c.id())));
        }
        
        if (parentCategoryComboBox != null) {
            parentCategoryComboBox.setItems(FXCollections.observableArrayList(allCategories));

            if (editingCategory != null && editingCategory.parentId() != null) {
                allCategories.stream()
                        .filter(c -> c != null && c.id() != null && c.id().equals(editingCategory.parentId()))
                        .findFirst()
                        .ifPresent(parentCategoryComboBox::setValue);
            }
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
            showAlert("Error", "Failed to " + action + " category: " + ErrorHandler.toUserMessage(e));
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

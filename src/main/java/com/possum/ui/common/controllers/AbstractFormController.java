package com.possum.ui.common.controllers;

import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.validation.FormValidator;
import com.possum.ui.navigation.Parameterizable;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Abstract base controller for form views (create/edit/view).
 * Eliminates duplicate code across UserForm, CustomerForm, ProductForm, etc.
 * 
 * @param <T> Entity type (User, Customer, Product, etc.)
 */
public abstract class AbstractFormController<T> implements Parameterizable {

    @FXML protected Label titleLabel;
    @FXML protected Button saveButton;
    @FXML protected Button cancelButton;

    protected final WorkspaceManager workspaceManager;
    protected final FormValidator formValidator = new FormValidator();
    
    protected Long entityId = null;
    protected FormMode mode = FormMode.CREATE;

    /**
     * Form modes: CREATE, EDIT, VIEW
     */
    public enum FormMode {
        CREATE, EDIT, VIEW
    }

    protected AbstractFormController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params == null) {
            setupCreateMode();
            return;
        }

        // Extract entity ID
        Object idParam = params.get(getEntityIdParamName());
        if (idParam instanceof Long id) {
            this.entityId = id;
        } else if (idParam instanceof Integer intId) {
            this.entityId = intId.longValue();
        }

        // Extract mode
        String modeParam = (String) params.get("mode");
        if ("view".equalsIgnoreCase(modeParam)) {
            mode = FormMode.VIEW;
        } else if ("edit".equalsIgnoreCase(modeParam)) {
            mode = FormMode.EDIT;
        } else {
            mode = FormMode.CREATE;
        }

        // Setup based on mode
        if (entityId != null) {
            if (mode == FormMode.VIEW) {
                setupViewMode();
            } else {
                setupEditMode();
            }
        } else {
            setupCreateMode();
        }
    }

    /**
     * Get the parameter name for entity ID (e.g., "userId", "customerId", "productId")
     */
    protected abstract String getEntityIdParamName();

    /**
     * Get entity display name for titles (e.g., "User", "Customer", "Product")
     */
    protected abstract String getEntityDisplayName();

    /**
     * Load entity by ID
     */
    protected abstract T loadEntity(Long id);

    /**
     * Populate form fields with entity data
     */
    protected abstract void populateFields(T entity);

    /**
     * Setup form validators
     */
    protected abstract void setupValidators();

    /**
     * Validate form inputs
     * @return true if valid, false otherwise
     */
    protected boolean validateForm() {
        return formValidator.validate();
    }

    /**
     * Create new entity from form data
     */
    protected abstract void createEntity() throws Exception;

    /**
     * Update existing entity from form data
     */
    protected abstract void updateEntity() throws Exception;

    /**
     * Setup form for create mode
     */
    protected void setupCreateMode() {
        mode = FormMode.CREATE;
        entityId = null;
        
        if (titleLabel != null) {
            titleLabel.setText("Add " + getEntityDisplayName());
        }
        
        setupValidators();
        setFormEditable(true);
    }

    /**
     * Setup form for edit mode
     */
    protected void setupEditMode() {
        mode = FormMode.EDIT;
        
        if (titleLabel != null) {
            titleLabel.setText("Edit " + getEntityDisplayName());
        }
        
        T entity = null;
        try {
            entity = loadEntity(entityId);
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load entity for editing", e);
            NotificationService.error("Failed to load " + getEntityDisplayName() + ": " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
            closeForm();
            return;
        }

        if (entity != null) {
            populateFields(entity);
        } else {
            NotificationService.error(getEntityDisplayName() + " not found.");
            closeForm();
            return;
        }
        
        setupValidators();
        setFormEditable(true);
    }

    /**
     * Setup form for view mode (read-only)
     */
    protected void setupViewMode() {
        mode = FormMode.VIEW;
        
        if (titleLabel != null) {
            titleLabel.setText("View " + getEntityDisplayName());
        }
        
        T entity = null;
        try {
            entity = loadEntity(entityId);
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load entity for viewing", e);
            NotificationService.error("Failed to load " + getEntityDisplayName() + ": " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
            closeForm();
            return;
        }

        if (entity != null) {
            populateFields(entity);
        } else {
            NotificationService.error(getEntityDisplayName() + " not found.");
            closeForm();
            return;
        }
        
        setFormEditable(false);
        
        if (saveButton != null) {
            saveButton.setVisible(false);
            saveButton.setManaged(false);
        }
    }

    /**
     * Set form editable or read-only
     */
    protected abstract void setFormEditable(boolean editable);

    /**
     * Handle save button click
     */
    @FXML
    protected void handleSave() {
        if (!validateForm()) {
            NotificationService.warning("Please fix the highlighted fields");
            return;
        }

        if (saveButton != null) {
            saveButton.setDisable(true);
            saveButton.setText("Saving...");
        }

        try {
            if (mode == FormMode.CREATE) {
                createEntity();
                NotificationService.success(getEntityDisplayName() + " created successfully");
            } else {
                updateEntity();
                NotificationService.success(getEntityDisplayName() + " updated successfully");
            }
            
            closeForm();
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to save " + getEntityDisplayName(), e);
            NotificationService.error("Failed to save " + getEntityDisplayName() + ": " + com.possum.ui.common.ErrorHandler.toUserMessage(e)); 
            
            if (saveButton != null) {
                saveButton.setDisable(false);
                saveButton.setText(mode == FormMode.CREATE ? "Create" : "Save");
            }
        }
    }

    /**
     * Handle cancel button click
     */
    @FXML
    protected void handleCancel() {
        closeForm();
    }

    /**
     * Close the form
     */
    protected void closeForm() {
        if (titleLabel != null) {
            workspaceManager.close(titleLabel);
        } else {
            workspaceManager.closeActiveWindow();
        }
    }

    /**
     * Replace a control with a read-only label
     */
    protected void replaceWithLabel(Control control, String text) {
        if (control == null || control.getParent() == null) return;
        
        Label label = new Label(text != null && !text.isEmpty() ? text : "-");
        label.getStyleClass().add("readonly-value");
        label.setWrapText(true);
        
        javafx.scene.Parent parent = control.getParent();
        if (parent instanceof VBox box) {
            int index = box.getChildren().indexOf(control);
            if (index != -1) {
                box.getChildren().set(index, label);
            }
        } else if (parent instanceof HBox box) {
            int index = box.getChildren().indexOf(control);
            if (index != -1) {
                box.getChildren().set(index, label);
            }
        }
    }

    /**
     * Replace a TextField with a read-only label
     */
    protected void replaceWithLabel(TextField field) {
        replaceWithLabel(field, field != null ? field.getText() : null);
    }

    /**
     * Replace a TextArea with a read-only label
     */
    protected void replaceWithLabel(TextArea field) {
        replaceWithLabel(field, field != null ? field.getText() : null);
    }

    /**
     * Replace a ComboBox with a read-only label
     */
    protected void replaceWithLabel(ComboBox<?> combo) {
        String text = null;
        if (combo != null && combo.getValue() != null) {
            text = combo.getValue().toString();
        }
        replaceWithLabel(combo, text);
    }

    /**
     * Check if form is in create mode
     */
    protected boolean isCreateMode() {
        return mode == FormMode.CREATE;
    }

    /**
     * Check if form is in edit mode
     */
    protected boolean isEditMode() {
        return mode == FormMode.EDIT;
    }

    /**
     * Check if form is in view mode
     */
    protected boolean isViewMode() {
        return mode == FormMode.VIEW;
    }

    /**
     * Get the entity ID (null if creating)
     */
    protected Long getEntityId() {
        return entityId;
    }
}

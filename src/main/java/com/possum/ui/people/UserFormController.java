package com.possum.ui.people;

import com.possum.application.people.UserService;
import com.possum.domain.model.User;
import com.possum.ui.common.controllers.AbstractFormController;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Collections;

public class UserFormController extends AbstractFormController<User> {

    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Label passwordHelper;

    private final UserService userService;

    public UserFormController(UserService userService, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.userService = userService;
    }

    @FXML
    public void initialize() {
        if (statusCombo != null) {
            statusCombo.setItems(FXCollections.observableArrayList("Active", "Inactive"));
            statusCombo.setValue("Active");
        }
        setupValidators();
    }

    @Override
    protected String getEntityIdParamName() {
        return "userId";
    }

    @Override
    protected String getEntityDisplayName() {
        return "Employee";
    }

    @Override
    protected User loadEntity(Long id) {
        return userService.getUserById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    protected void populateFields(User user) {
        if (nameField != null) {
            nameField.setText(user.name());
        }
        if (usernameField != null) {
            usernameField.setText(user.username());
        }
        if (statusCombo != null) {
            statusCombo.setValue(Boolean.TRUE.equals(user.active()) ? "Active" : "Inactive");
        }
        
        // Update password helper text based on mode
        if (passwordHelper != null) {
            if (isEditMode()) {
                passwordHelper.setText("Leave blank to keep current password.");
            } else if (isCreateMode()) {
                passwordHelper.setText("Required for new employees.");
            }
        }
    }

    @Override
    protected void setupValidators() {
        if (nameField != null) {
            com.possum.ui.common.validation.FieldValidator.of(nameField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Full name"))
                .validateOnType();
        }

        if (usernameField != null) {
            com.possum.ui.common.validation.FieldValidator.of(usernameField)
                .addValidator(com.possum.ui.common.validation.Validators.required("Username"))
                .addValidator(com.possum.ui.common.validation.Validators.noSpaces("Username"))
                .validateOnType();
        }

        if (statusCombo != null) {
             com.possum.ui.common.validation.FieldValidator.of(statusCombo)
                .addValidator(com.possum.ui.common.validation.Validators.notNull("Status"))
                .validateOnFocusLost(); // Combo updates are better on focus lost or selection change
        }

        if (passwordField != null) {
             com.possum.ui.common.validation.FieldValidator.of(passwordField)
                .addValidator(val -> {
                    if (isCreateMode() && (val == null || val.isEmpty())) 
                        return com.possum.ui.common.validation.ValidationResult.error("Password is required");
                    if (val != null && !val.isEmpty() && val.length() < 6) 
                        return com.possum.ui.common.validation.ValidationResult.error("Min 6 characters required");
                    return com.possum.ui.common.validation.ValidationResult.success();
                })
                .validateOnType();
        }
    }

    @Override
    protected boolean validateForm() {
        return super.validateForm();
    }

    @Override
    protected void setFormEditable(boolean editable) {
        if (!editable) {
            // View mode - replace fields with labels
            if (nameField != null) replaceWithLabel(nameField);
            if (usernameField != null) replaceWithLabel(usernameField);
            if (statusCombo != null) replaceWithLabel(statusCombo);
            
            // Hide password field in view mode
            if (passwordField != null && passwordField.getParent() instanceof javafx.scene.layout.VBox box) {
                box.getChildren().remove(passwordField);
            }
            if (passwordHelper != null && passwordHelper.getParent() instanceof javafx.scene.layout.VBox box) {
                box.getChildren().remove(passwordHelper);
            }
        } else {
            // Edit/Create mode - fields are already editable
            if (nameField != null) nameField.setEditable(true);
            if (usernameField != null) usernameField.setEditable(true);
            if (statusCombo != null) statusCombo.setDisable(false);
            if (passwordField != null) passwordField.setEditable(true);
        }
    }

    @Override
    protected void createEntity() throws Exception {
        boolean isActive = "Active".equals(statusCombo.getValue());
        
        userService.createUser(
            nameField.getText().trim(),
            usernameField.getText().trim(),
            passwordField.getText(),
            isActive,
            Collections.emptyList() // Roles are managed separately
        );
    }

    @Override
    protected void updateEntity() throws Exception {
        boolean isActive = "Active".equals(statusCombo.getValue());
        
        userService.updateUser(
            getEntityId(),
            nameField.getText().trim(),
            usernameField.getText().trim(),
            passwordField.getText(), // Allows blank if not updating
            isActive,
            null // Prevent roles from being overwritten
        );
    }
}

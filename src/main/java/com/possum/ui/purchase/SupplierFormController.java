package com.possum.ui.purchase;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.model.Supplier;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.common.controllers.AbstractFormController;
import com.possum.ui.common.validation.FieldValidator;
import com.possum.ui.common.validation.Validators;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class SupplierFormController extends AbstractFormController<Supplier> {

    private final SupplierRepository supplierRepository;

    @FXML private TextField nameField;
    @FXML private TextField contactPersonField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextArea addressField;
    @FXML private TextField gstinField;
    @FXML private ComboBox<PaymentPolicy> paymentPolicyCombo;

    private Runnable onSaveCallback;

    public SupplierFormController(SupplierRepository supplierRepository, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.supplierRepository = supplierRepository;
    }

    @Override
    public void setParameters(java.util.Map<String, Object> params) {
        super.setParameters(params);
        if (params != null && params.containsKey("onSave")) {
            this.onSaveCallback = (Runnable) params.get("onSave");
        }
        loadPaymentPolicies();
    }

    @Override
    protected String getEntityIdParamName() {
        return "supplierId";
    }

    @Override
    protected String getEntityDisplayName() {
        return "Supplier";
    }

    private void loadPaymentPolicies() {
        Platform.runLater(() -> {
            try {
                List<PaymentPolicy> policies = supplierRepository.getPaymentPolicies();
                setupPolicyCombo(policies);
            } catch (Exception e) {
                NotificationService.error("Failed to load payment policies");
            }
        });
    }

    private void setupPolicyCombo(List<PaymentPolicy> allPolicies) {
        paymentPolicyCombo.setItems(FXCollections.observableArrayList(allPolicies));
        paymentPolicyCombo.setConverter(new javafx.util.StringConverter<PaymentPolicy>() {
            @Override
            public String toString(PaymentPolicy p) {
                return p == null ? "" : p.name();
            }
            @Override
            public PaymentPolicy fromString(String string) {
                return allPolicies.stream().filter(p -> p.name().equals(string)).findFirst().orElse(null);
            }
        });
        paymentPolicyCombo.setEditable(false);
    }

    @Override
    protected Supplier loadEntity(Long id) {
        return supplierRepository.findSupplierById(id)
                .orElseThrow(() -> new com.possum.domain.exceptions.NotFoundException("Supplier not found: " + id));
    }

    @Override
    protected void populateFields(Supplier supplier) {
        nameField.setText(supplier.name());
        contactPersonField.setText(supplier.contactPerson() != null ? supplier.contactPerson() : "");
        phoneField.setText(supplier.phone() != null ? supplier.phone() : "");
        emailField.setText(supplier.email() != null ? supplier.email() : "");
        addressField.setText(supplier.address() != null ? supplier.address() : "");
        gstinField.setText(supplier.gstin() != null ? supplier.gstin() : "");

        if (supplier.paymentPolicyId() != null) {
            supplierRepository.getPaymentPolicies().stream()
                .filter(p -> p.id().equals(supplier.paymentPolicyId()))
                .findFirst()
                .ifPresent(p -> paymentPolicyCombo.setValue(p));
        }
    }

    @Override
    protected void setupValidators() {
        FieldValidator.of(nameField)
            .addValidator(Validators.required("Supplier name is required"))
            .validateOnFocusLost();

        FieldValidator.of(phoneField)
            .addValidator(Validators.pattern("[+0-9()\\\\-\\\\s]{7,20}", "Enter a valid phone number"))
            .validateOnFocusLost();

        FieldValidator.of(emailField)
            .addValidator(Validators.email())
            .validateOnFocusLost();
    }

    @Override
    protected void createEntity() throws Exception {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        
        PaymentPolicy selectedPolicy = paymentPolicyCombo.getValue();
        Long policyId = selectedPolicy != null ? selectedPolicy.id() : null;
        String policyName = selectedPolicy != null ? selectedPolicy.name() : null;

        Supplier supplier = new Supplier(
                null,
                nameField.getText().trim(),
                contactPersonField.getText().trim().isEmpty() ? null : contactPersonField.getText().trim(),
                phoneField.getText().trim().isEmpty() ? null : phoneField.getText().trim(),
                emailField.getText().trim().isEmpty() ? null : emailField.getText().trim(),
                addressField.getText().trim().isEmpty() ? null : addressField.getText().trim(),
                gstinField.getText().trim().isEmpty() ? null : gstinField.getText().trim(),
                policyId,
                policyName,
                null, null, null
        );

        supplierRepository.createSupplier(supplier);
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
    }

    @Override
    protected void updateEntity() throws Exception {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        
        PaymentPolicy selectedPolicy = paymentPolicyCombo.getValue();
        Long policyId = selectedPolicy != null ? selectedPolicy.id() : null;
        String policyName = selectedPolicy != null ? selectedPolicy.name() : null;

        Supplier supplier = new Supplier(
                entityId,
                nameField.getText().trim(),
                contactPersonField.getText().trim().isEmpty() ? null : contactPersonField.getText().trim(),
                phoneField.getText().trim().isEmpty() ? null : phoneField.getText().trim(),
                emailField.getText().trim().isEmpty() ? null : emailField.getText().trim(),
                addressField.getText().trim().isEmpty() ? null : addressField.getText().trim(),
                gstinField.getText().trim().isEmpty() ? null : gstinField.getText().trim(),
                policyId,
                policyName,
                null, null, null
        );

        supplierRepository.updateSupplier(entityId, supplier);
        if (onSaveCallback != null) {
            onSaveCallback.run();
        }
    }

    @Override
    protected void setFormEditable(boolean editable) {
        nameField.setEditable(editable);
        contactPersonField.setEditable(editable);
        phoneField.setEditable(editable);
        emailField.setEditable(editable);
        addressField.setEditable(editable);
        gstinField.setEditable(editable);
        paymentPolicyCombo.setDisable(!editable);
    }

    @FXML
    private void handleManagePolicies() {
        workspaceManager.openWindow("Payment Policies", "/fxml/purchase/payment-policies-view.fxml", null);
        // Refresh policies when return
        loadPaymentPolicies();
    }
}

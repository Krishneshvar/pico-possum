package com.possum.ui.purchase;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.ui.common.controllers.AbstractFormController;
import com.possum.ui.common.validation.FieldValidator;
import com.possum.ui.common.validation.Validators;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Map;

public class PaymentPolicyFormController extends AbstractFormController<PaymentPolicy> {

    private final SupplierRepository supplierRepository;

    @FXML private TextField nameField;
    @FXML private TextField daysToPayField;
    @FXML private TextArea descriptionField;

    private Runnable onSaveCallback;

    public PaymentPolicyFormController(SupplierRepository supplierRepository, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.supplierRepository = supplierRepository;
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        super.setParameters(params);
        if (params != null && params.containsKey("onSave")) {
            this.onSaveCallback = (Runnable) params.get("onSave");
        }
    }

    @Override
    protected String getEntityIdParamName() {
        return "policyId";
    }

    @Override
    protected String getEntityDisplayName() {
        return "Payment Policy";
    }

    @Override
    protected PaymentPolicy loadEntity(Long id) {
        return supplierRepository.getPaymentPolicies().stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new com.possum.domain.exceptions.NotFoundException("Policy not found: " + id));
    }

    @Override
    protected void populateFields(PaymentPolicy policy) {
        nameField.setText(policy.name());
        daysToPayField.setText(String.valueOf(policy.daysToPay()));
        descriptionField.setText(policy.description() != null ? policy.description() : "");
    }

    @Override
    protected void setupValidators() {
        FieldValidator.of(nameField)
                .addValidator(Validators.required("Policy name is required"))
                .validateOnFocusLost();

        FieldValidator.of(daysToPayField)
                .addValidator(Validators.required("Days to pay is required"))
                .addValidator(Validators.pattern("\\d+", "Must be a valid number of days"))
                .validateOnFocusLost();
    }

    @Override
    protected void createEntity() throws Exception {
        String name = nameField.getText().trim();
        int days = Integer.parseInt(daysToPayField.getText().trim());
        String desc = descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim();

        supplierRepository.createPaymentPolicy(name, days, desc);
        if (onSaveCallback != null) onSaveCallback.run();
    }

    @Override
    protected void updateEntity() throws Exception {
        String name = nameField.getText().trim();
        int days = Integer.parseInt(daysToPayField.getText().trim());
        String desc = descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim();

        supplierRepository.updatePaymentPolicy(entityId, name, days, desc);
        if (onSaveCallback != null) onSaveCallback.run();
    }

    @Override
    protected void setFormEditable(boolean editable) {
        nameField.setEditable(editable);
        daysToPayField.setEditable(editable);
        descriptionField.setEditable(editable);
    }
}

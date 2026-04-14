package com.possum.ui.taxes;

import com.possum.application.taxes.TaxExemptionService;
import com.possum.application.people.CustomerService;
import com.possum.domain.model.Customer;
import com.possum.domain.model.TaxExemption;
import com.possum.ui.common.toast.ToastService;
import com.possum.ui.navigation.Parameterizable;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.Map;

public class TaxExemptionFormController implements Parameterizable {
    
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private ComboBox<String> exemptionTypeCombo;
    @FXML private TextField certificateField;
    @FXML private TextArea reasonArea;
    @FXML private DatePicker validFromPicker;
    @FXML private DatePicker validToPicker;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    
    private final TaxExemptionService taxExemptionService;
    private final CustomerService customerService;
    private final ToastService toastService;
    
    private Long exemptionId;
    private String mode = "add";

    public TaxExemptionFormController(TaxExemptionService taxExemptionService, 
                                      CustomerService customerService,
                                      ToastService toastService) {
        this.taxExemptionService = taxExemptionService;
        this.customerService = customerService;
        this.toastService = toastService;
    }

    @FXML
    public void initialize() {
        exemptionTypeCombo.getItems().addAll("GOVERNMENT", "NGO", "DIPLOMATIC", "EXPORT", "OTHER");
        var filter = new com.possum.shared.dto.CustomerFilter(null, 1, 1000, 1, 1000, "name", "ASC");
        customerCombo.getItems().addAll(customerService.getCustomers(filter).items());
        customerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Customer customer) {
                return customer != null ? customer.name() : "";
            }
            @Override
            public Customer fromString(String string) {
                return null;
            }
        });
        
        validFromPicker.setValue(LocalDate.now());
        validToPicker.setValue(LocalDate.now().plusYears(1));
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params.containsKey("exemptionId")) {
            exemptionId = (Long) params.get("exemptionId");
            mode = "edit";
            loadExemption();
        }
    }

    private void loadExemption() {
        var exemptionOpt = taxExemptionService.getExemptionById(exemptionId);
        if (exemptionOpt.isPresent()) {
            var exemption = exemptionOpt.get();
            var customer = customerService.getCustomerById(exemption.customerId());
            customer.ifPresent(customerCombo::setValue);
            exemptionTypeCombo.setValue(exemption.exemptionType());
            certificateField.setText(exemption.certificateNumber());
            reasonArea.setText(exemption.reason());
            validFromPicker.setValue(exemption.validFrom().toLocalDate());
            validToPicker.setValue(exemption.validTo().toLocalDate());
        }
    }

    @FXML
    private void handleSave() {
        if (!validate()) return;
        
        try {
            if ("edit".equals(mode)) {
                taxExemptionService.updateExemption(
                    exemptionId,
                    exemptionTypeCombo.getValue(),
                    certificateField.getText(),
                    reasonArea.getText(),
                    validFromPicker.getValue().atStartOfDay(),
                    validToPicker.getValue().atStartOfDay(),
                    1L
                );
                toastService.success("Tax exemption updated successfully");
            } else {
                taxExemptionService.createExemption(
                    customerCombo.getValue().id(),
                    exemptionTypeCombo.getValue(),
                    certificateField.getText(),
                    reasonArea.getText(),
                    validFromPicker.getValue().atStartOfDay(),
                    validToPicker.getValue().atStartOfDay(),
                    1L
                );
                toastService.success("Tax exemption created successfully");
            }
            closeDialog();
        } catch (Exception e) {
            toastService.error("Failed to save tax exemption: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private boolean validate() {
        if (customerCombo.getValue() == null) {
            toastService.error("Please select a customer");
            return false;
        }
        if (exemptionTypeCombo.getValue() == null) {
            toastService.error("Please select exemption type");
            return false;
        }
        if (certificateField.getText().trim().isEmpty()) {
            toastService.error("Certificate number is required");
            return false;
        }
        if (reasonArea.getText().trim().isEmpty()) {
            toastService.error("Reason is required");
            return false;
        }
        if (validFromPicker.getValue() == null || validToPicker.getValue() == null) {
            toastService.error("Valid dates are required");
            return false;
        }
        if (validToPicker.getValue().isBefore(validFromPicker.getValue())) {
            toastService.error("Valid To date must be after Valid From date");
            return false;
        }
        return true;
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}

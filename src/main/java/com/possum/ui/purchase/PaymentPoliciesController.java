package com.possum.ui.purchase;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentPoliciesController extends AbstractCrudController<PaymentPolicy, String> {

    @FXML private Button createButton;

    private final SupplierRepository supplierRepository;

    public PaymentPoliciesController(SupplierRepository supplierRepository, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.supplierRepository = supplierRepository;
    }

    @Override
    protected void setupPermissions() {
        if (createButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(createButton, com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
            FontIcon plusIcon = new FontIcon("bx-plus");
            plusIcon.setIconSize(16);
            plusIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            createButton.setGraphic(plusIcon);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No payment policies found. Click 'Create Policy' to add one.");
        
        TableColumn<PaymentPolicy, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        nameCol.setPrefWidth(200);

        TableColumn<PaymentPolicy, Integer> daysCol = new TableColumn<>("Days to Pay");
        daysCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().daysToPay()).asObject());
        daysCol.setPrefWidth(120);

        TableColumn<PaymentPolicy, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().description()));
        descCol.setPrefWidth(300);

        dataTable.getTableView().getColumns().addAll(nameCol, daysCol, descCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        // Simple text filter only
        setupStandardFilterListener((filters, reload) -> reload.run());
    }

    @Override
    protected String buildFilter() {
        return getSearchOrNull();
    }

    @Override
    protected PagedResult<PaymentPolicy> fetchData(String filter) {
        // Note: Repository currently doesn't support paged/filtered policies, so we'll do client-side filtering if needed
        // but for now, we'll just return all and wrap in PagedResult
        List<PaymentPolicy> all = supplierRepository.getPaymentPolicies();
        
        if (filter != null && !filter.isBlank()) {
            all = all.stream()
                .filter(p -> p.name().toLowerCase().contains(filter.toLowerCase()) || 
                             (p.description() != null && p.description().toLowerCase().contains(filter.toLowerCase())))
                .toList();
        }
        
        return new PagedResult<>(all, all.size(), 1, 1, all.size());
    }

    @Override
    protected String getEntityName() {
        return "payment-policies";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Payment Policy";
    }

    @Override
    protected List<MenuItem> buildActionMenu(PaymentPolicy policy) {
        return com.possum.ui.common.components.MenuBuilder.create()
            .addEditAction("Edit Policy", () -> handleEdit(policy))
            .addSeparator()
            .addDeleteAction("Delete Policy", () -> handleDelete(policy))
            .build();
    }

    @Override
    protected void deleteEntity(PaymentPolicy entity) throws Exception {
        supplierRepository.deletePaymentPolicy(entity.id());
    }

    @Override
    protected String getEntityIdentifier(PaymentPolicy entity) {
        return entity.name();
    }

    @FXML
    private void handleCreate() {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        Map<String, Object> params = new HashMap<>();
        params.put("onSave", (Runnable) this::loadData);
        workspaceManager.openWindow("Add Payment Policy", "/fxml/purchase/payment-policy-form-view.fxml", params);
    }

    private void handleEdit(PaymentPolicy policy) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        Map<String, Object> params = new HashMap<>();
        params.put("policyId", policy.id());
        params.put("onSave", (Runnable) this::loadData);
        workspaceManager.openWindow("Edit Payment Policy", "/fxml/purchase/payment-policy-form-view.fxml", params);
    }
}

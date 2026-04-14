package com.possum.ui.purchase;

import com.possum.domain.model.PaymentPolicy;
import com.possum.domain.model.Supplier;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SupplierFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.beans.property.SimpleStringProperty;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuppliersController extends AbstractCrudController<Supplier, SupplierFilter> {
    
    @FXML private javafx.scene.control.Button createButton;
    
    private final SupplierRepository supplierRepository;
    private List<Long> currentPolicyIds = null;

    public SuppliersController(SupplierRepository supplierRepository, WorkspaceManager workspaceManager) {
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
        dataTable.setEmptyMessage("No suppliers found. Click 'Create Supplier' to add one.");
        
        TableColumn<Supplier, String> gstinCol = new TableColumn<>("GSTIN");
        gstinCol.setPrefWidth(140);
        gstinCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().gstin() != null ? cellData.getValue().gstin() : "-"));
        
        TableColumn<Supplier, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Supplier, String> contactCol = new TableColumn<>("Contact Person");
        contactCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().contactPerson()));
        
        TableColumn<Supplier, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().phone()));
        
        TableColumn<Supplier, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email()));

        TableColumn<Supplier, String> policyCol = new TableColumn<>("Policy");
        policyCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().paymentPolicyName() != null ? cellData.getValue().paymentPolicyName() : "-"));

        dataTable.getTableView().getColumns().addAll(gstinCol, nameCol, contactCol, phoneCol, emailCol, policyCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        try {
            List<PaymentPolicy> policies = supplierRepository.getPaymentPolicies();
            filterBar.addMultiSelectFilter("policy", "All Policies", policies, PaymentPolicy::name, false);
        } catch(Exception e) {}

        setupStandardFilterListener((filters, reload) -> {
            List<PaymentPolicy> selectedPolicies = (List<PaymentPolicy>) filters.get("policy");
            if (selectedPolicies == null || selectedPolicies.isEmpty()) {
                currentPolicyIds = null;
            } else {
                currentPolicyIds = selectedPolicies.stream().map(PaymentPolicy::id).toList();
            }
            reload.run();
        });
    }

    @Override
    protected SupplierFilter buildFilter() {
        return new SupplierFilter(
            getCurrentPage(),
            getPageSize(),
            getSearchOrNull(),
            currentPolicyIds,
            "name",
            "ASC"
        );
    }

    @Override
    protected PagedResult<Supplier> fetchData(SupplierFilter filter) {
        return supplierRepository.getAllSuppliers(filter);
    }

    @Override
    protected String getEntityName() {
        return "suppliers";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Supplier";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Supplier supplier) {
        return com.possum.ui.common.components.MenuBuilder.create()
            .addViewAction("View Details", () -> workspaceManager.openWindow(
                "View Supplier: " + supplier.name(), 
                "/fxml/purchase/supplier-form-view.fxml", 
                Map.of("supplierId", supplier.id(), "mode", "view")))
            .addEditAction("Edit Supplier", () -> handleEdit(supplier))
            .addSeparator()
            .addDeleteAction("Delete Supplier", () -> handleDelete(supplier))
            .build();
    }

    @Override
    protected void deleteEntity(Supplier entity) throws Exception {
        supplierRepository.deleteSupplier(entity.id());
    }

    @Override
    protected String getEntityIdentifier(Supplier entity) {
        return entity.name();
    }

    @FXML
    private void handleCreate() {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        Map<String, Object> params = new HashMap<>();
        params.put("onSave", (Runnable) this::loadData);
        workspaceManager.openWindow("Add Supplier", "/fxml/purchase/supplier-form-view.fxml", params);
    }

    private void handleEdit(Supplier supplier) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SUPPLIERS_MANAGE);
        Map<String, Object> params = new HashMap<>();
        params.put("supplierId", supplier.id());
        params.put("onSave", (Runnable) this::loadData);
        workspaceManager.openWindow("Edit Supplier", "/fxml/purchase/supplier-form-view.fxml", params);
    }
}

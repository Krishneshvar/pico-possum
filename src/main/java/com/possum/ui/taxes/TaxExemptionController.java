package com.possum.ui.taxes;

import com.possum.application.taxes.TaxExemptionService;
import com.possum.domain.model.TaxExemption;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.MenuItem;
import javafx.beans.property.SimpleStringProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class TaxExemptionController extends AbstractCrudController<TaxExemption, TaxExemptionController.TaxExemptionFilter> {
    
    @FXML private javafx.scene.control.Button addButton;
    
    private final TaxExemptionService taxExemptionService;
    private final com.possum.application.people.CustomerService customerService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TaxExemptionController(TaxExemptionService taxExemptionService, 
                                   com.possum.application.people.CustomerService customerService,
                                   WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.taxExemptionService = taxExemptionService;
        this.customerService = customerService;
    }

    @Override
    protected void setupPermissions() {
        if (addButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(addButton, 
                com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.getTableView().setPlaceholder(new javafx.scene.control.Label("No tax exemptions found"));
        
        TableColumn<TaxExemption, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cellData -> {
            var customer = customerService.getCustomerById(cellData.getValue().customerId());
            return new SimpleStringProperty(customer.map(c -> c.name()).orElse("Unknown"));
        });
        
        TableColumn<TaxExemption, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().exemptionType()));
        
        TableColumn<TaxExemption, String> certCol = new TableColumn<>("Certificate");
        certCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().certificateNumber()));
        
        TableColumn<TaxExemption, String> validFromCol = new TableColumn<>("Valid From");
        validFromCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().validFrom().format(DATE_FORMATTER)));
        
        TableColumn<TaxExemption, String> validToCol = new TableColumn<>("Valid To");
        validToCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().validTo().format(DATE_FORMATTER)));
        
        TableColumn<TaxExemption, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> {
            boolean active = isExemptionActive(cellData.getValue());
            return new SimpleStringProperty(active ? "Active" : "Expired");
        });
        
        dataTable.getTableView().getColumns().addAll(customerCol, typeCol, certCol, validFromCol, validToCol, statusCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        setupStandardFilterListener();
    }

    @Override
    protected TaxExemptionFilter buildFilter() {
        return new TaxExemptionFilter(getSearchOrNull(), getCurrentPage() + 1, getPageSize());
    }

    @Override
    protected PagedResult<TaxExemption> fetchData(TaxExemptionFilter filter) {
        var exemptions = taxExemptionService.getCustomerExemptions(null);
        if (exemptions == null) exemptions = java.util.List.of();
        
        var filtered = exemptions.stream()
            .filter(e -> filter.search() == null || 
                customerService.getCustomerById(e.customerId()).map(c -> c.name().toLowerCase().contains(filter.search().toLowerCase())).orElse(false))
            .skip((long) (filter.page() - 1) * filter.pageSize())
            .limit(filter.pageSize())
            .toList();
        
        int totalPages = (int) Math.ceil((double) exemptions.size() / filter.pageSize());
        return new PagedResult<>(filtered, exemptions.size(), totalPages, filter.page(), filter.pageSize());
    }

    @Override
    protected String getEntityName() {
        return "tax exemptions";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Tax Exemption";
    }

    @Override
    protected List<MenuItem> buildActionMenu(TaxExemption exemption) {
        if (!com.possum.ui.common.UIPermissionUtil.hasPermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE)) {
            return List.of();
        }

        return com.possum.ui.common.components.MenuBuilder.create()
            .addEditAction("Edit", () -> workspaceManager.openDialog(
                "Edit Tax Exemption", 
                "/fxml/taxes/tax-exemption-form.fxml", 
                Map.of("exemptionId", exemption.id(), "mode", "edit")))
            .addSeparator()
            .addDeleteAction("Revoke", () -> handleDelete(exemption))
            .build();
    }

    @Override
    protected void deleteEntity(TaxExemption entity) throws Exception {
        taxExemptionService.deleteExemption(entity.id(), 1L);
    }

    @Override
    protected String getEntityIdentifier(TaxExemption entity) {
        var customer = customerService.getCustomerById(entity.customerId());
        return customer.map(c -> c.name()).orElse("Unknown Customer");
    }

    @FXML
    private void handleAdd() {
        workspaceManager.openDialog("Add Tax Exemption", "/fxml/taxes/tax-exemption-form.fxml");
    }
    
    private boolean isExemptionActive(TaxExemption exemption) {
        LocalDateTime now = LocalDateTime.now();
        return !exemption.validFrom().isAfter(now) && !exemption.validTo().isBefore(now);
    }

    public record TaxExemptionFilter(String search, int page, int pageSize) {}
}

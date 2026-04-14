package com.possum.ui.people;

import com.possum.application.people.CustomerService;
import com.possum.domain.model.Customer;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.CustomerFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.controllers.AbstractImportController;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.CsvImportUtil;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.MenuItem;
import javafx.beans.property.SimpleStringProperty;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class CustomersController extends AbstractCrudController<Customer, CustomerFilter> {
    
    @FXML private javafx.scene.control.Button addButton;
    
    private final CustomerService customerService;
    private final ImportHandler importHandler;

    public CustomersController(CustomerService customerService, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.customerService = customerService;
        this.importHandler = new ImportHandler();
    }

    @Override
    protected void setupPermissions() {
        if (addButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(addButton, com.possum.application.auth.Permissions.CUSTOMERS_MANAGE);
        }
        if (importHandler.importButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(importHandler.importButton, com.possum.application.auth.Permissions.CUSTOMERS_MANAGE);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.getTableView().setPlaceholder(new javafx.scene.control.Label("No customers found"));
        
        TableColumn<Customer, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        
        TableColumn<Customer, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setSortable(false);
        phoneCol.setCellValueFactory(cellData -> {
            String phone = cellData.getValue().phone();
            return new SimpleStringProperty(phone != null && !phone.isEmpty() ? phone : "-");
        });
        
        TableColumn<Customer, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> {
            String email = cellData.getValue().email();
            return new SimpleStringProperty(email != null && !email.isEmpty() ? email : "-");
        });
        
        TableColumn<Customer, String> addressCol = new TableColumn<>("Address");
        addressCol.setSortable(false);
        addressCol.setCellValueFactory(cellData -> {
            String addr = cellData.getValue().address();
            return new SimpleStringProperty(addr != null && !addr.isEmpty() ? addr : "-");
        });
        
        dataTable.getTableView().getColumns().addAll(nameCol, phoneCol, emailCol, addressCol);
        addActionMenuColumn();
    }

    @Override
    protected void setupFilters() {
        setupStandardFilterListener();
    }

    @Override
    protected CustomerFilter buildFilter() {
        return new CustomerFilter(
            getSearchOrNull(),
            getCurrentPage() + 1,
            getPageSize(),
            getCurrentPage() + 1,
            getPageSize(),
            "name",
            "ASC"
        );
    }

    @Override
    protected PagedResult<Customer> fetchData(CustomerFilter filter) {
        return customerService.getCustomers(filter);
    }

    @Override
    protected String getEntityName() {
        return "customers";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Customer";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Customer customer) {
        if (!com.possum.ui.common.UIPermissionUtil.hasPermission(com.possum.application.auth.Permissions.CUSTOMERS_MANAGE)) {
            return List.of();
        }

        return com.possum.ui.common.components.MenuBuilder.create()
            .addEditAction("Edit", () -> workspaceManager.openDialog(
                "Edit Customer: " + customer.name(), 
                "/fxml/people/customer-form-view.fxml", 
                Map.of("customerId", customer.id(), "mode", "edit")))
            .addSeparator()
            .addDeleteAction("Delete", () -> handleDelete(customer))
            .build();
    }

    @Override
    protected void deleteEntity(Customer entity) throws Exception {
        customerService.deleteCustomer(entity.id());
    }

    @Override
    protected String getEntityIdentifier(Customer entity) {
        return entity.name();
    }

    @FXML
    private void handleAdd() {
        workspaceManager.openDialog("Add Customer", "/fxml/people/customer-form-view.fxml");
    }

    @FXML
    private void handleImport() {
        importHandler.handleImport();
    }

    /**
     * Inner class to handle CSV import functionality
     */
    private class ImportHandler extends AbstractImportController<Customer, CustomerImportRow> {

        @Override
        protected String[] getRequiredHeaders() {
            return new String[]{"Customer Name", "Name"};
        }

        @Override
        protected CustomerImportRow parseRow(List<String> row, Map<String, Integer> headers) {
            String name = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Customer Name", "Name"));
            if (name == null) {
                return null;
            }

            String phone = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Contact Number", "Phone"));
            String email = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Email"));
            String address = buildAddress(
                CsvImportUtil.getValue(row, headers, "Customer Address", "Address"),
                CsvImportUtil.getValue(row, headers, "City"),
                CsvImportUtil.getValue(row, headers, "Pin Code", "Pincode", "Postal Code")
            );

            return new CustomerImportRow(name, phone, email, address);
        }

        @Override
        protected Customer createEntity(CustomerImportRow record) throws Exception {
            return customerService.createCustomer(record.name(), record.phone(), record.email(), record.address());
        }

        @Override
        protected String getImportTitle() {
            return "Import Customers from CSV";
        }

        @Override
        protected String getEntityName() {
            return "customer(s)";
        }

        @Override
        protected void onImportComplete() {
            loadData();
        }

        private String buildAddress(String address, String city, String pinCode) {
            StringJoiner joiner = new StringJoiner(", ");

            String normalizedAddress = CsvImportUtil.emptyToNull(address);
            String normalizedCity = CsvImportUtil.emptyToNull(city);
            String normalizedPin = CsvImportUtil.emptyToNull(pinCode);

            if (normalizedAddress != null) joiner.add(normalizedAddress);
            if (normalizedCity != null) joiner.add(normalizedCity);
            if (normalizedPin != null) joiner.add(normalizedPin);

            String merged = joiner.toString();
            return merged.isEmpty() ? null : merged;
        }
    }

    private record CustomerImportRow(String name, String phone, String email, String address) {}
}

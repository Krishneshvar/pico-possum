package com.possum.ui.audit;

import com.possum.application.audit.AuditService;
import com.possum.domain.model.AuditLog;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.components.BadgeFactory;
import com.possum.ui.common.dialogs.DialogStyler;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.TimeUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AuditController extends AbstractCrudController<AuditLog, AuditLogFilter> {
    
    private final AuditService auditService;
    private List<String> currentActions = null;
    private String startDateStr = null;
    private String endDateStr = null;

    public AuditController(AuditService auditService, WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.auditService = auditService;
    }

    @Override
    protected void setupPermissions() {
        // Audit logs are read-only, no special permissions needed
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No audit logs found");
        dataTable.setEmptySubtitle("Audit events will appear here as they occur.");
        
        TableColumn<AuditLog, String> userCol = new TableColumn<>("User");
        userCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().userName()));
        
        TableColumn<AuditLog, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().action()));
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = createActionBadge(item);
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });
        
        TableColumn<AuditLog, String> tableCol = new TableColumn<>("Table");
        tableCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().tableName()));
        
        TableColumn<AuditLog, Long> rowCol = new TableColumn<>("Row ID");
        rowCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().rowId()));
        
        TableColumn<AuditLog, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().createdAt()));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    LocalDateTime localTime = TimeUtil.toLocal(item);
                    setText(localTime != null ? TimeUtil.formatStandard(localTime) : "");
                }
            }
        });
        
        dataTable.getTableView().getColumns().addAll(userCol, actionCol, tableCol, rowCol, dateCol);
        dataTable.addActionColumn("Details", this::showDetails);
    }

    @Override
    protected void setupFilters() {
        List<String> actions = List.of("CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT");
        filterBar.addMultiSelectFilter("actions", "All Actions", actions, String::toString);
        filterBar.addDateFilter("startDate", "From Date");
        filterBar.addDateFilter("endDate", "To Date");
    }

    @Override
    protected AuditLogFilter buildFilter() {
        String searchTerm = filterBar.getSearchTerm();
        
        @SuppressWarnings("unchecked")
        List<String> selectedActions = (List<String>) filterBar.getFilterValue("actions");
        currentActions = (selectedActions == null || selectedActions.isEmpty()) ? null : selectedActions;
        
        LocalDate start = (LocalDate) filterBar.getFilterValue("startDate");
        LocalDate end = (LocalDate) filterBar.getFilterValue("endDate");
        
        startDateStr = start != null ? start.toString() : null;
        endDateStr = end != null ? end.toString() : null;
        
        return new AuditLogFilter(
            null,
            null,
            null,
            currentActions,
            startDateStr,
            endDateStr,
            searchTerm == null || searchTerm.isEmpty() ? null : searchTerm,
            "created_at",
            "DESC",
            getCurrentPage(),
            getPageSize()
        );
    }

    @Override
    protected PagedResult<AuditLog> fetchData(AuditLogFilter filter) {
        return auditService.listAuditEvents(filter);
    }

    @Override
    protected String getEntityName() {
        return "audit logs";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Audit Log Entry";
    }

    @Override
    protected List<MenuItem> buildActionMenu(AuditLog entity) {
        return List.of(); // Audit logs are read-only
    }

    @Override
    protected void deleteEntity(AuditLog entity) throws Exception {
        throw new UnsupportedOperationException("Audit logs cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(AuditLog entity) {
        return "Event #" + entity.id();
    }

    private Label createActionBadge(String action) {
        String upperAction = action.toUpperCase();
        return switch (upperAction) {
            case "CREATE", "LOGIN" -> BadgeFactory.createSuccessBadge(upperAction);
            case "UPDATE" -> BadgeFactory.createBadge(upperAction, "badge-info");
            case "DELETE" -> BadgeFactory.createErrorBadge(upperAction);
            case "LOGOUT" -> BadgeFactory.createBadge(upperAction, "badge-secondary");
            default -> BadgeFactory.createWarningBadge(upperAction);
        };
    }

    private void showDetails(AuditLog log) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DialogStyler.apply(alert);
        alert.setTitle("Audit Log Details");
        alert.setHeaderText("Event #" + log.id());
        
        StringBuilder content = new StringBuilder();
        content.append("User: ").append(log.userName()).append("\n");
        content.append("Action: ").append(log.action()).append("\n");
        content.append("Table: ").append(log.tableName()).append("\n");
        content.append("Row ID: ").append(log.rowId()).append("\n");
        content.append("Date: ").append(log.createdAt()).append("\n\n");
        
        if (log.oldData() != null) {
            content.append("Old Data:\n").append(log.oldData()).append("\n\n");
        }
        if (log.newData() != null) {
            content.append("New Data:\n").append(log.newData()).append("\n\n");
        }
        if (log.eventDetails() != null) {
            content.append("Event Details:\n").append(log.eventDetails()).append("\n");
        }
        
        alert.setContentText(content.toString());
        alert.showAndWait();
    }
}

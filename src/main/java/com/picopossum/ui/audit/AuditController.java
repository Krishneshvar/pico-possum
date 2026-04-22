package com.picopossum.ui.audit;

import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.ui.common.controllers.AbstractCrudController;
import com.picopossum.ui.common.components.BadgeFactory;
import com.picopossum.ui.common.dialogs.DialogStyler;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.shared.util.TimeUtil;
import com.picopossum.infrastructure.system.AppExecutor;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import com.picopossum.ui.common.controls.NotificationService;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class AuditController extends AbstractCrudController<AuditLog, AuditLogFilter> {
    
    private final AuditService auditService;
    private List<String> currentActions = null;
    private String startDateStr = null;
    private String endDateStr = null;

    public AuditController(AuditService auditService, WorkspaceManager workspaceManager, AppExecutor executor) {
        super(workspaceManager, executor);
        this.auditService = auditService;
    }

    @Override
    protected void initUIComponents() {
        // Audit logs are read-only
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No audit logs found");
        dataTable.setEmptySubtitle("Audit events will appear here as they occur.");
        
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
        
        TableColumn<AuditLog, String> tableCol = new TableColumn<>("Module");
        tableCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().tableName() != null ? cellData.getValue().tableName() : "System"
        ));
        
        TableColumn<AuditLog, Long> rowCol = new TableColumn<>("Entity ID");
        rowCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().rowId()));
        
        TableColumn<AuditLog, LocalDateTime> dateCol = new TableColumn<>("Timestamp");
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
        
        dataTable.getTableView().getColumns().addAll(actionCol, tableCol, rowCol, dateCol);
        dataTable.addActionColumn("View Changes", this::showDetails);
    }

    @Override
    protected void setupFilters() {
        List<String> actions = List.of("CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT");
        filterBar.addMultiSelectFilter("actions", "All Actions", actions, String::toString);
        filterBar.addDateFilter("startDate", "From Date");
        filterBar.addDateFilter("endDate", "To Date");
        
        setupStandardFilterListener();
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
        return List.of();
    }

    @Override
    protected void deleteEntity(AuditLog entity) throws Exception {
        throw new UnsupportedOperationException("Audit logs are permanent system records");
    }

    @Override
    protected String getEntityIdentifier(AuditLog entity) {
        return "Log #" + entity.id();
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
        Dialog<Void> dialog = new Dialog<>();
        DialogStyler.apply(dialog);
        dialog.setTitle("Audit Event Details");
        dialog.setHeaderText("Log Entry #" + log.id() + " - " + log.action());
        
        VBox content = new VBox(15);
        content.setMinWidth(600);
        content.setPrefWidth(650);
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);
        grid.add(new Label("Timestamp:"), 0, 0);
        grid.add(new Label(TimeUtil.formatStandard(TimeUtil.toLocal(log.createdAt()))), 1, 0);
        grid.add(new Label("Module:"), 0, 1);
        grid.add(new Label(log.tableName() != null ? log.tableName() : "System"), 1, 1);
        grid.add(new Label("Entity ID:"), 0, 2);
        grid.add(new Label(String.valueOf(log.rowId())), 1, 2);
        
        content.getChildren().add(grid);
        
        if (log.oldData() != null || log.newData() != null) {
            HBox diffBox = new HBox(15);
            diffBox.setPrefHeight(300);
            
            if (log.oldData() != null) {
                VBox oldVBox = new VBox(5, new Label("PREVIOUS STATE"), createJsonArea(log.oldData()));
                HBox.setHgrow(oldVBox, Priority.ALWAYS);
                diffBox.getChildren().add(oldVBox);
            }
            if (log.newData() != null) {
                VBox newVBox = new VBox(5, new Label("NEW STATE"), createJsonArea(log.newData()));
                HBox.setHgrow(newVBox, Priority.ALWAYS);
                diffBox.getChildren().add(newVBox);
            }
            content.getChildren().add(diffBox);
        }
        
        if (log.eventDetails() != null && !log.eventDetails().isBlank()) {
            content.getChildren().addAll(new Label("EVENT DETAILS"), new Label(log.eventDetails()));
        }
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private TextArea createJsonArea(String json) {
        TextArea area = new TextArea(json);
        area.setEditable(false);
        area.setWrapText(true);
        area.setStyle("-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 12px; -fx-opacity: 0.8;");
        return area;
    }


    @Override
    @FXML
    public void handleRefresh() {
        loadData();
        NotificationService.success("Audit log refreshed");
    }
}

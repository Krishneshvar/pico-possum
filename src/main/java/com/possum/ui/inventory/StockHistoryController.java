package com.possum.ui.inventory;

import com.possum.application.inventory.InventoryService;
import com.possum.application.people.UserService;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.model.User;
import com.possum.shared.dto.UserFilter;
import com.possum.shared.dto.StockHistoryDto;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.components.BadgeFactory;
import com.possum.ui.common.components.ButtonFactory;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.TimeUtil;
import com.possum.shared.util.TextFormatter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class StockHistoryController extends AbstractCrudController<StockHistoryDto, StockHistoryFilter> {

    @FXML private Button refreshButton;

    private final InventoryService inventoryService;
    private final UserService userService;

    private List<String> currentReasons = null;
    private LocalDate currentFromDate = null;
    private LocalDate currentToDate = null;
    private List<Long> currentUserIds = null;

    public StockHistoryController(InventoryService inventoryService, 
                                  UserService userService,
                                  WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.inventoryService = inventoryService;
        this.userService = userService;
    }

    @Override
    protected void setupPermissions() {
        if (refreshButton != null) {
            ButtonFactory.applyRefreshButtonStyle(refreshButton);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No stock history found");
        dataTable.setEmptySubtitle("Try adjusting filters or search terms.");
        
        TableColumn<StockHistoryDto, String> productCol = new TableColumn<>("Product");
        TableColumn<StockHistoryDto, String> variantCol = new TableColumn<>("Variant");
        TableColumn<StockHistoryDto, String> skuCol = new TableColumn<>("SKU");
        TableColumn<StockHistoryDto, String> changeCol = new TableColumn<>("Change");
        TableColumn<StockHistoryDto, String> reasonCol = new TableColumn<>("Reason");
        TableColumn<StockHistoryDto, String> adjustedByCol = new TableColumn<>("Adjusted By");
        TableColumn<StockHistoryDto, String> dateCol = new TableColumn<>("Date & Time");

        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().variantName()));
        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        
        changeCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().quantityChange() > 0 
                    ? "+" + cellData.getValue().quantityChange() 
                    : String.valueOf(cellData.getValue().quantityChange())));
        changeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(item.startsWith("+") 
                        ? "-fx-text-fill: green; -fx-font-weight: bold; -fx-alignment: center-right;" 
                        : "-fx-text-fill: red; -fx-font-weight: bold; -fx-alignment: center-right;");
                }
            }
        });
        
        reasonCol.setCellValueFactory(cellData -> {
            String reason = cellData.getValue().reason();
            if (reason == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(formatReason(reason));
        });
        reasonCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = BadgeFactory.createBadge(item, "badge-info");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });
        reasonCol.setSortable(false);
        
        adjustedByCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().adjustedByName()));
        adjustedByCol.setSortable(false);
        
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().adjustedAt() != null 
                    ? TimeUtil.formatStandard(TimeUtil.toLocal(cellData.getValue().adjustedAt())) 
                    : ""));

        dataTable.getTableView().getColumns().addAll(
            productCol, variantCol, skuCol, changeCol, reasonCol, adjustedByCol, dateCol
        );
    }

    @Override
    protected void setupFilters() {
        filterBar.addMultiSelectFilter("reasons", "Filter by Reasons", List.of(InventoryReason.values()), 
            item -> formatReason(item.getValue()), false);

        DatePicker fromDate = filterBar.addDateFilter("fromDate", "From Date");
        DatePicker toDate = filterBar.addDateFilter("toDate", "To Date");

        // Date validation
        toDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && fromDate.getValue() != null && newVal.isBefore(fromDate.getValue())) {
                toDate.setValue(fromDate.getValue());
            }
        });

        fromDate.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && toDate.getValue() != null && newVal.isAfter(toDate.getValue())) {
                toDate.setValue(newVal);
            }
        });

        // Load users asynchronously
        CompletableFuture.supplyAsync(() -> userService.getUsers(new UserFilter(null, 1, 1000, null, null)).items())
                .thenAccept(users -> Platform.runLater(() -> {
                    filterBar.addMultiSelectFilter("adjustedBy", "Filter by User", users, User::name, true);
                }));

        setupDatePickerFormat(fromDate);
        setupDatePickerFormat(toDate);
    }

    @Override
    protected StockHistoryFilter buildFilter() {
        String searchTerm = filterBar.getSearchTerm();
        
        Object reasonsObj = filterBar.getFilterValue("reasons");
        if (reasonsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<InventoryReason> selectedReasons = (List<InventoryReason>) reasonsObj;
            if (!selectedReasons.isEmpty()) {
                currentReasons = selectedReasons.stream().map(InventoryReason::getValue).toList();
            } else {
                currentReasons = null;
            }
        } else {
            currentReasons = null;
        }
        
        currentFromDate = (LocalDate) filterBar.getFilterValue("fromDate");
        currentToDate = (LocalDate) filterBar.getFilterValue("toDate");

        Object usersObj = filterBar.getFilterValue("adjustedBy");
        if (usersObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<User> selectedUsers = (List<User>) usersObj;
            if (!selectedUsers.isEmpty()) {
                currentUserIds = selectedUsers.stream().map(User::id).toList();
            } else {
                currentUserIds = null;
            }
        } else {
            currentUserIds = null;
        }

        return new StockHistoryFilter(
            searchTerm,
            currentReasons != null ? currentReasons : new ArrayList<>(),
            currentFromDate,
            currentToDate,
            currentUserIds,
            getCurrentPage(),
            getPageSize()
        );
    }

    @Override
    protected PagedResult<StockHistoryDto> fetchData(StockHistoryFilter filter) {
        String fromDateStr = filter.fromDate() != null 
            ? filter.fromDate().atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) 
            : null;
        String toDateStr = filter.toDate() != null 
            ? filter.toDate().atTime(23, 59, 59).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) 
            : null;

        int offset = (filter.page() - 1) * filter.limit();
        
        List<StockHistoryDto> results = inventoryService.getStockHistory(
            filter.searchTerm(), 
            filter.reasons(), 
            fromDateStr, 
            toDateStr, 
            filter.userIds(), 
            filter.limit(), 
            offset
        );
        
        // Since the service doesn't return total count, we estimate it
        int totalCount = offset + results.size() + (results.size() == filter.limit() ? 1 : 0);
        int totalPages = (int) Math.ceil((double) totalCount / filter.limit());
        
        return new PagedResult<>(results, totalCount, totalPages, filter.page(), filter.limit());
    }

    @Override
    protected String getEntityName() {
        return "stock history";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Stock History Entry";
    }

    @Override
    protected List<MenuItem> buildActionMenu(StockHistoryDto entity) {
        return List.of(); // Stock history is read-only
    }

    @Override
    protected void deleteEntity(StockHistoryDto entity) throws Exception {
        throw new UnsupportedOperationException("Stock history cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(StockHistoryDto entity) {
        return entity.productName() + " (" + entity.variantName() + ")";
    }

    private String formatReason(String reason) {
        if (reason == null) return "";
        if ("confirm_receive".equalsIgnoreCase(reason)) return "Received";
        return TextFormatter.camelCaseToWords(reason.replace("_", " "));
    }

    private void setupDatePickerFormat(DatePicker picker) {
        picker.setConverter(new javafx.util.StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                if (date != null) {
                    return TimeUtil.getDateFormatter().format(date);
                } else {
                    return "";
                }
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    try {
                        return LocalDate.parse(string, TimeUtil.getDateFormatter());
                    } catch (Exception e) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        });
        
        picker.setPromptText("DD/MM/YYYY");
    }
}

// Filter record for stock history
record StockHistoryFilter(
    String searchTerm,
    List<String> reasons,
    LocalDate fromDate,
    LocalDate toDate,
    List<Long> userIds,
    int page,
    int limit
) {}

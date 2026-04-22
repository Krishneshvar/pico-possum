package com.picopossum.ui.inventory;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.people.UserService;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.shared.dto.StockHistoryDto;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.ui.common.controllers.AbstractCrudController;
import com.picopossum.ui.common.components.BadgeFactory;
import com.picopossum.ui.common.components.ButtonFactory;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.infrastructure.system.AppExecutor;
import com.picopossum.ui.common.controls.DateControlUtils;
import com.picopossum.shared.util.TimeUtil;
import com.picopossum.shared.util.TextFormatter;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                                  WorkspaceManager workspaceManager,
                                  AppExecutor executor) {
        super(workspaceManager, executor);
        this.inventoryService = inventoryService;
        this.userService = userService;
    }

    @Override
    protected void initUIComponents() {
        if (refreshButton != null) {
            ButtonFactory.applyRefreshButtonStyle(refreshButton);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No stock history found");
        dataTable.setEmptySubtitle("Try adjusting filters or search terms.");
        
        TableColumn<StockHistoryDto, String> skuCol = new TableColumn<>("SKU");
        TableColumn<StockHistoryDto, String> productCol = new TableColumn<>("Product");
        TableColumn<StockHistoryDto, String> changeCol = new TableColumn<>("Change");
        TableColumn<StockHistoryDto, String> reasonCol = new TableColumn<>("Reason");
        TableColumn<StockHistoryDto, Integer> currentStockCol = new TableColumn<>("Current Stock");
        TableColumn<StockHistoryDto, String> dateCol = new TableColumn<>("Date & Time");

        skuCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().sku()));
        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        
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
                        ? "-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-alignment: center-right;" 
                        : "-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-alignment: center-right;");
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
                    String styleClass = switch (item.toLowerCase()) {
                        case "sale" -> "badge-success";
                        case "return" -> "badge-info";
                        case "damage", "theft", "spoilage" -> "badge-danger";
                        default -> "badge-neutral";
                    };
                    Label badge = BadgeFactory.createBadge(item, styleClass);
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });
        reasonCol.setSortable(false);

        currentStockCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().currentStock()));
        currentStockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    StockHistoryDto dto = getTableView().getItems().get(getIndex());
                    int alertCap = dto.stockAlertCap() != null ? dto.stockAlertCap() : 10;
                    
                    Label label = new Label(String.valueOf(item));
                    if (item <= 0) {
                        label.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                    } else if (item <= alertCap) {
                        label.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                    } else {
                        label.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                    }
                    setGraphic(label);
                }
            }
        });
        
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().createdAt() != null 
                    ? TimeUtil.formatStandard(TimeUtil.toLocal(cellData.getValue().createdAt())) 
                    : ""));

        dataTable.getTableView().getColumns().addAll(
            skuCol, productCol, changeCol, reasonCol, currentStockCol, dateCol
        );
    }

    @Override
    protected void setupFilters() {
        filterBar.addMultiSelectFilter("reasons", "Filter by Reasons", List.of(InventoryReason.values()), 
            item -> formatReason(item.getValue()), false);

        DatePicker fromDate = filterBar.addDateFilter("fromDate", "From Date");
        DatePicker toDate = filterBar.addDateFilter("toDate", "To Date");

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

        DateControlUtils.applyStandardFormat(fromDate);
        DateControlUtils.applyStandardFormat(toDate);
        
        setupStandardFilterListener();
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

        return new StockHistoryFilter(
            searchTerm,
            currentReasons != null ? currentReasons : new ArrayList<>(),
            currentFromDate,
            currentToDate,
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
            filter.limit(), 
            offset
        );
        
        int totalCount = offset + results.size() + (results.size() == filter.limit() ? 1 : 0);
        int totalPages = (int) Math.ceil((double) totalCount / filter.limit());
        
        return new PagedResult<>(results, totalCount, totalPages, filter.page(), filter.limit());
    }

    @Override
    protected String getEntityName() {
        return "stock activity";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Stock Activity Entry";
    }

    @Override
    protected List<MenuItem> buildActionMenu(StockHistoryDto entity) {
        return List.of(); 
    }

    @Override
    protected void deleteEntity(StockHistoryDto entity) throws Exception {
        throw new UnsupportedOperationException("Stock history cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(StockHistoryDto entity) {
        return entity.productName();
    }

    private String formatReason(String reason) {
        if (reason == null) return "";
        if ("confirm_receive".equalsIgnoreCase(reason)) return "Received";
        if ("product_deleted".equalsIgnoreCase(reason)) return "Deleted";
        return TextFormatter.camelCaseToWords(reason.replace("_", " "));
    }

    @FXML
    protected void handleRefresh() {
        loadData();
        com.picopossum.ui.common.controls.NotificationService.success("Stock activity log refreshed");
    }
}

record StockHistoryFilter(
    String searchTerm,
    List<String> reasons,
    LocalDate fromDate,
    LocalDate toDate,
    int page,
    int limit
) {}

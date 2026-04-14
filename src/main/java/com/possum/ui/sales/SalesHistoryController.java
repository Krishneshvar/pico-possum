package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.shared.util.TimeUtil;
import com.possum.application.sales.SalesService;
import com.possum.domain.model.Sale;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SaleFilter;
import com.possum.ui.common.controls.FilterBar;
import com.possum.ui.common.controls.PaginationBar;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.ui.common.controls.*;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SalesHistoryController {

    @FXML private VBox container;
    @FXML private VBox mainCard;
    @FXML private DataTableView<Sale> salesTable;

    @FXML private PaginationBar paginationBar;
    @FXML private Button importLegacyButton;

    private FilterBar filterBar;
    private final SalesService salesService;
    private final SettingsStore settingsStore;
    private final PrinterService printerService;
    private final WorkspaceManager workspaceManager;
    private final ObservableList<Sale> salesList = FXCollections.observableArrayList();

    private int currentPage = 1;
    private int pageSize = 15;
    private String currentSearch = "";
    private List<String> currentStatus = null;
    private java.time.LocalDate currentFromDate = null;
    private java.time.LocalDate currentToDate = null;
    private BigDecimal currentMinAmount = null;
    private BigDecimal currentMaxAmount = null;
    private List<Long> currentPaymentMethodIds = null;

    private SalesHistoryActionsHandler actionsHandler;
    private LegacySaleImporter importer;

    public SalesHistoryController(SalesService salesService, 
                                  SettingsStore settingsStore, 
                                  PrinterService printerService,
                                  WorkspaceManager workspaceManager) {
        this.salesService = salesService;
        this.settingsStore = settingsStore;
        this.printerService = printerService;
        this.workspaceManager = workspaceManager;
    }

    @FXML
    public void initialize() {
        if (importLegacyButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(importLegacyButton, com.possum.application.auth.Permissions.SALES_MANAGE);
        }

        actionsHandler = new SalesHistoryActionsHandler(salesService, settingsStore, printerService, workspaceManager, this::loadHistory);
        importer = new LegacySaleImporter(salesService, salesTable != null && salesTable.getScene() != null ? salesTable.getScene().getWindow() : null, this::loadHistory);

        setupTable();
        setupFilters();
        setupPagination();
        loadHistory();
    }

    private void setupTable() {
        salesTable.getTableView().setItems(salesList);

        salesTable.addColumn("Invoice #", cellData -> new SimpleStringProperty(cellData.getValue().shortInvoiceNumber()));
        
        salesTable.addColumn("Customer", cellData -> new SimpleStringProperty(
                cellData.getValue().customerName() != null ? cellData.getValue().customerName() : "Walk-in Customer"));
        
        salesTable.addColumn("Payment", cellData -> new SimpleStringProperty(
                cellData.getValue().paymentMethodName() != null ? cellData.getValue().paymentMethodName() : "-"));

        TableColumn<Sale, BigDecimal> amountCol = (TableColumn<Sale, BigDecimal>) salesTable.addColumn("Bill Total", cellData -> new SimpleObjectProperty<>(cellData.getValue().totalAmount()));
        amountCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(item));
                }
            }
        });
        amountCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Sale, String> statusCol = (TableColumn<Sale, String>) salesTable.addColumn("Status", cellData -> new SimpleStringProperty(cellData.getValue().status()));
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createSaleStatusBadge(item));
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(javafx.geometry.Pos.CENTER);
                    setText(null);
                }
            }
        });
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setSortable(false);

        salesTable.addColumn("Date & Time", cellData -> {
            LocalDateTime saleDate = cellData.getValue().saleDate();
            if (saleDate == null) return new SimpleStringProperty("");
            LocalDateTime localZoned = TimeUtil.toLocal(saleDate);
            return new SimpleStringProperty(TimeUtil.formatStandard(localZoned));
        });

        salesTable.addMenuActionColumn("Actions", actionsHandler::buildActionsMenu);
    }

    private void setupFilters() {
        filterBar = new FilterBar();
        
        filterBar.addMultiSelectFilter("status", "All Statuses", 
                List.of("Paid", "Partially Paid", "Draft", "Cancelled", "Refunded", "Partially Refunded", "Legacy"),
                s -> s,
                false
        );

        List<com.possum.domain.model.PaymentMethod> pms = salesService.getPaymentMethods();
        filterBar.addMultiSelectFilter("paymentMethod", "All Payments", pms, 
                com.possum.domain.model.PaymentMethod::name,
                false);
        filterBar.addDateFilter("fromDate", "From Date");
        filterBar.addDateFilter("toDate", "To Date");
        filterBar.addTextFilter("minAmount", "Min Total");
        filterBar.addTextFilter("maxAmount", "Max Total");
        
        filterBar.setOnFilterChange(filters -> {
            currentSearch = (String) filters.get("search");
            currentFromDate = (java.time.LocalDate) filters.get("fromDate");
            currentToDate = (java.time.LocalDate) filters.get("toDate");

            Object min = filters.get("minAmount");
            currentMinAmount = parseBigDecimal(min);

            Object max = filters.get("maxAmount");
            currentMaxAmount = parseBigDecimal(max);
            
            List<String> statuses = (List<String>) filters.get("status");
            if (statuses == null || statuses.isEmpty()) {
                currentStatus = null;
            } else {
                currentStatus = statuses.stream()
                        .map(s -> s.toLowerCase().replace(" ", "_"))
                        .toList();
            }

            List<com.possum.domain.model.PaymentMethod> selectedPms = (List<com.possum.domain.model.PaymentMethod>) filters.get("paymentMethod");
            if (selectedPms == null || selectedPms.isEmpty()) {
                currentPaymentMethodIds = null;
            } else {
                currentPaymentMethodIds = selectedPms.stream()
                        .map(com.possum.domain.model.PaymentMethod::id)
                        .toList();
            }

            paginationBar.reset();
            loadHistory();
        });

        mainCard.getChildren().add(0, filterBar);
    }

    private void setupPagination() {
        paginationBar.setOnPageChange((page, size) -> {
            currentPage = page;
            pageSize = size;
            loadHistory();
        });
    }

    @FXML
    public void loadHistory() {
        SaleFilter filter = new SaleFilter(
                currentStatus,
                null, // customerId
                null, // userId
                currentFromDate != null ? currentFromDate.atStartOfDay().toString() : null,
                currentToDate != null ? currentToDate.atTime(23, 59, 59).toString() : null,
                currentMinAmount,
                currentMaxAmount,
                currentSearch,
                currentPage,
                pageSize,
                "sale_date",
                "DESC",
                null, // fulfillmentStatus
                currentPaymentMethodIds
        );

        salesTable.setLoading(true);
        AuthUser currentUser = AuthContext.getCurrentUser();
        CompletableFuture.runAsync(() -> {
            AuthContext.setCurrentUser(currentUser);
            try {
                PagedResult<Sale> results = salesService.findSales(filter);

                Platform.runLater(() -> {
                    salesList.setAll(results.items());
                    paginationBar.setTotalItems(results.totalCount());
                    salesTable.setLoading(false);
                });
            } finally {
                AuthContext.clear();
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            Platform.runLater(() -> salesTable.setLoading(false));
            return null;
        });
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            String s = value.toString().trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FXML
    private void handleImportLegacySales() {
        importer.handleImport();
    }
}

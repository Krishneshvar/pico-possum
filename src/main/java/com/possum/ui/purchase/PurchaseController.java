package com.possum.ui.purchase;

import com.possum.application.auth.AuthContext;
import com.possum.application.purchase.PurchaseService;
import com.possum.domain.model.PurchaseOrder;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;
import com.possum.ui.common.controls.*;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.scene.control.Label;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import com.possum.ui.common.dialogs.DialogStyler;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.possum.shared.util.TimeUtil;
import com.possum.shared.util.CurrencyUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchaseController {
    
    @FXML private FilterBar filterBar;
    @FXML private DataTableView<PurchaseOrder> purchaseTable;
    @FXML private PaginationBar paginationBar;
    @FXML private javafx.scene.control.Button createButton;
    
    private PurchaseService purchaseService;
    private com.possum.application.sales.SalesService salesService;
    private WorkspaceManager workspaceManager;
    private String currentSearch = "";
    private List<String> currentStatuses = null;
    private List<Long> currentPaymentMethodIds = null;
    private java.time.LocalDate currentFromDate = null;
    private java.time.LocalDate currentToDate = null;
    private java.math.BigDecimal currentMinPrice = null;
    private java.math.BigDecimal currentMaxPrice = null;

    public PurchaseController(PurchaseService purchaseService, 
                              com.possum.application.sales.SalesService salesService,
                              WorkspaceManager workspaceManager) {
        this.purchaseService = purchaseService;
        this.salesService = salesService;
        this.workspaceManager = workspaceManager;
    }

    @FXML
    public void initialize() {
        if (createButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(createButton, com.possum.application.auth.Permissions.PURCHASE_MANAGE);
            FontIcon plusIcon = new FontIcon("bx-plus");
            plusIcon.setIconSize(16);
            plusIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            createButton.setGraphic(plusIcon);
        }
        setupTable();
        setupFilters();
        loadPurchaseOrders();
    }

    private void setupTable() {
        TableColumn<PurchaseOrder, String> idCol = new TableColumn<>("PO Invoice #");
        idCol.setSortable(false);
        idCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().invoiceNumber() != null ? cellData.getValue().invoiceNumber() : ("#" + cellData.getValue().id())));
        idCol.setPrefWidth(120);
        idCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    getStyleClass().add("text-bold");
                    setStyle("-fx-text-fill: black;");
                    setGraphic(null);
                }
            }
        });
        
        TableColumn<PurchaseOrder, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().supplierName()));
        supplierCol.setPrefWidth(200);
        supplierCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        
        TableColumn<PurchaseOrder, String> paymentCol = new TableColumn<>("Payment Method");
        paymentCol.setSortable(false);
        paymentCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().paymentMethodName() != null ? cellData.getValue().paymentMethodName() : "-"));
        
        TableColumn<PurchaseOrder, Integer> itemsCol = new TableColumn<>("Items");
        itemsCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().itemCount()));
        itemsCol.setPrefWidth(80);
        itemsCol.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<PurchaseOrder, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().totalCost() != null ? CurrencyUtil.format(cellData.getValue().totalCost()) : CurrencyUtil.format(java.math.BigDecimal.ZERO)));
        priceCol.setPrefWidth(100);
        priceCol.getStyleClass().add("text-bold");
        priceCol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: black;");

        TableColumn<PurchaseOrder, String> statusCol = new TableColumn<>("Status");
        statusCol.setSortable(false);
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        statusCol.setPrefWidth(120);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createPurchaseStatusBadge(status));
                }
            }
        });
        
        TableColumn<PurchaseOrder, LocalDateTime> dateCol = new TableColumn<>("Order Date");
        dateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().orderDate()));
        dateCol.setPrefWidth(150);
        dateCol.setCellFactory(col -> {
            return new TableCell<>() {
                @Override
                protected void updateItem(LocalDateTime item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        LocalDateTime localTime = TimeUtil.toLocal(item);
                        setText(localTime != null ? TimeUtil.formatStandard(localTime) : "");
                        setStyle("-fx-text-fill: gray;");
                    }
                }
            };
        });

        purchaseTable.getTableView().getColumns().setAll(java.util.Arrays.asList(idCol, supplierCol, paymentCol, itemsCol, priceCol, statusCol, dateCol));
        purchaseTable.addMenuActionColumn("Actions", this::buildActionsMenu);
        purchaseTable.setEmptyMessage("No purchase orders found. Click '+ New Purchase Order' to create one.");
    }

    private void setupFilters() {
        filterBar.addMultiSelectFilter("status", "All Statuses", 
                java.util.List.of("Pending", "Received", "Cancelled"),
                s -> s,
                false
        );

        java.util.List<com.possum.domain.model.PaymentMethod> pms = salesService.getPaymentMethods();
        filterBar.addMultiSelectFilter("paymentMethod", "All Payments", pms, 
                com.possum.domain.model.PaymentMethod::name,
                false);
        
        filterBar.addDateFilter("fromDate", "From Date");
        filterBar.addDateFilter("toDate", "To Date");
        filterBar.addTextFilter("minPrice", "Min Price");
        filterBar.addTextFilter("maxPrice", "Max Price");

        filterBar.setOnFilterChange(filters -> {
            currentSearch = (String) filters.get("search");
            
            java.util.List<String> statuses = (java.util.List<String>) filters.get("status");
            if (statuses == null || statuses.isEmpty()) {
                currentStatuses = null;
            } else {
                currentStatuses = statuses.stream().map(String::toLowerCase).toList();
            }

            java.util.List<com.possum.domain.model.PaymentMethod> selectedPms = (java.util.List<com.possum.domain.model.PaymentMethod>) filters.get("paymentMethod");
            if (selectedPms == null || selectedPms.isEmpty()) {
                currentPaymentMethodIds = null;
            } else {
                currentPaymentMethodIds = selectedPms.stream()
                        .map(com.possum.domain.model.PaymentMethod::id)
                        .toList();
            }

            currentFromDate = (java.time.LocalDate) filters.get("fromDate");
            currentToDate = (java.time.LocalDate) filters.get("toDate");

            currentMinPrice = null;
            try {
                String min = (String) filters.get("minPrice");
                if (min != null && !min.trim().isEmpty()) {
                    String cleanMin = min.replaceAll("[^\\d.]", "");
                    if (!cleanMin.isEmpty()) currentMinPrice = new java.math.BigDecimal(cleanMin);
                }
            } catch (Exception ignored) {}

            currentMaxPrice = null;
            try {
                String max = (String) filters.get("maxPrice");
                if (max != null && !max.trim().isEmpty()) {
                    String cleanMax = max.replaceAll("[^\\d.]", "");
                    if (!cleanMax.isEmpty()) currentMaxPrice = new java.math.BigDecimal(cleanMax);
                }
            } catch (Exception ignored) {}

            loadPurchaseOrders();
        });
        
        paginationBar.setOnPageChange((page, size) -> loadPurchaseOrders());
    }

    private void loadPurchaseOrders() {
        purchaseTable.setLoading(true);
        
        Platform.runLater(() -> {
            try {
                PurchaseOrderFilter filter = new PurchaseOrderFilter(
                    paginationBar.getCurrentPage(),
                    paginationBar.getPageSize(),
                    currentSearch.isEmpty() ? null : currentSearch,
                    currentStatuses,
                    currentFromDate != null ? currentFromDate.atStartOfDay().toString() : null,
                    currentToDate != null ? currentToDate.atTime(23, 59, 59).toString() : null,
                    "order_date",
                    "DESC",
                    currentPaymentMethodIds,
                    currentMinPrice,
                    currentMaxPrice
                );
                
                PagedResult<PurchaseOrder> result = purchaseService.getAllPurchaseOrders(filter);
                
                purchaseTable.setItems(FXCollections.observableArrayList(result.items()));
                paginationBar.setTotalItems(result.totalCount());
                purchaseTable.setLoading(false);
            } catch (Exception e) {
                purchaseTable.setLoading(false);
                NotificationService.error("Failed to load purchase orders");
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadPurchaseOrders();
    }

    @FXML
    private void handleCreate() {
        Map<String, Object> params = new HashMap<>();
        params.put("onSave", (Runnable) this::loadPurchaseOrders);
        workspaceManager.openWindow("Create Purchase Order", "/fxml/purchase/purchase-order-form-view.fxml", params);
    }

    private java.util.List<MenuItem> buildActionsMenu(PurchaseOrder po) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        
        MenuItem viewItem = new MenuItem("View Details");
        FontIcon viewIcon = new FontIcon("bx-show");
        viewIcon.setIconSize(14);
        viewIcon.getStyleClass().add("table-action-icon");
        viewItem.setGraphic(viewIcon);
        viewItem.setOnAction(e -> handleView(po));
        items.add(viewItem);
        
        if ("pending".equals(po.status().toLowerCase())) {
            MenuItem editItem = new MenuItem("Edit Order");
            FontIcon editIcon = new FontIcon("bx-pencil");
            editIcon.setIconSize(14);
            editIcon.getStyleClass().add("table-action-icon");
            editItem.setGraphic(editIcon);
            editItem.setOnAction(e -> handleEdit(po));
            
            MenuItem receiveItem = new MenuItem("Receive Order");
            FontIcon receiveIcon = new FontIcon("bx-check-double");
            receiveIcon.setIconSize(14);
            receiveIcon.getStyleClass().add("table-action-icon-success"); // Ensure this class or similar exists
            receiveIcon.setIconColor(javafx.scene.paint.Color.valueOf("#10b981"));
            receiveItem.setGraphic(receiveIcon);
            receiveItem.setOnAction(e -> handleReceive(po));
            
            MenuItem cancelItem = new MenuItem("Cancel Order");
            FontIcon cancelIcon = new FontIcon("bx-x-circle");
            cancelIcon.setIconSize(14);
            cancelIcon.getStyleClass().add("table-action-icon-danger");
            cancelIcon.setIconColor(javafx.scene.paint.Color.valueOf("#ef4444"));
            cancelItem.setGraphic(cancelIcon);
            cancelItem.setOnAction(e -> handleCancelOrder(po));
            
            items.addAll(java.util.Arrays.asList(new SeparatorMenuItem(), editItem, receiveItem, new SeparatorMenuItem(), cancelItem));
        }
        
        return items;
    }

    private void handleView(PurchaseOrder po) {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", po.id());
        params.put("onAction", (Runnable) this::loadPurchaseOrders);
        workspaceManager.openWindow("Purchase Order " + (po.invoiceNumber() != null ? po.invoiceNumber() : ("PO-" + po.id())), "/fxml/purchase/purchase-order-detail-view.fxml", params);
    }
    
    private void handleCancelOrder(PurchaseOrder po) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(confirm);
        confirm.setTitle("Cancel Purchase Order");
        confirm.setHeaderText("Cancel PO " + (po.invoiceNumber() != null ? po.invoiceNumber() : ("#" + po.id())) + "?");
        confirm.setContentText("This action cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    long userId = AuthContext.getCurrentUser().id();
                    purchaseService.cancelPurchaseOrder(po.id(), userId);
                    NotificationService.success("Purchase order cancelled");
                    loadPurchaseOrders();
                } catch (Exception e) {
                    NotificationService.error("Failed to cancel: " + e.getMessage());
                }
            }
        });
    }

    private void handleEdit(PurchaseOrder po) {
        if (!"pending".equals(po.status())) {
            NotificationService.warning("Only pending orders can be edited");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("order", po);
        params.put("onSave", (Runnable) this::loadPurchaseOrders);
        workspaceManager.openWindow("Edit Purchase Order", "/fxml/purchase/purchase-order-form-view.fxml", params);
    }

    private void handleReceive(PurchaseOrder po) {
        if (!"pending".equals(po.status())) {
            NotificationService.warning("Only pending orders can be received");
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        
        DialogStyler.apply(confirm);
        confirm.setTitle("Receive Purchase Order");
        confirm.setHeaderText("Receive PO " + (po.invoiceNumber() != null ? po.invoiceNumber() : ("#" + po.id())) + "?");
        confirm.setContentText("This will create inventory lots and update stock levels.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    long userId = AuthContext.getCurrentUser().id();
                    purchaseService.receivePurchaseOrder(po.id(), userId);
                    NotificationService.success("Purchase order received");
                    loadPurchaseOrders();
                } catch (Exception e) {
                    NotificationService.error("Failed to receive: " + e.getMessage());
                }
            }
        });
    }
}

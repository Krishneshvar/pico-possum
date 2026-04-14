package com.possum.ui.insights;

import com.possum.application.inventory.ProductFlowService;
import com.possum.application.products.ProductService;
import com.possum.domain.model.Product;
import com.possum.domain.model.ProductFlow;
import com.possum.domain.model.Variant;
import com.possum.ui.common.controls.*;
import com.possum.ui.sales.ProductSearchIndex;
import com.possum.shared.util.TimeUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import com.possum.domain.model.Sale;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.SaleResponse;
import com.possum.ui.workspace.WorkspaceManager;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.Objects;
import java.util.HashMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;



public class ProductFlowController {

    @FXML private VBox root;
    @FXML private FilterBar filterBar;
    @FXML private DataTableView<ProductFlow> flowTable;
    @FXML private PaginationBar paginationBar;
    
    @FXML private Label totalSoldLabel;
    @FXML private Label totalPurchasedLabel;
    @FXML private Label totalReturnedLabel;
    @FXML private Label netMovementLabel;

    private ComboBox<String> selectionTypeCombo;
    private MultiSelectFilter<String> eventTypeFilter;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;

    @FXML private HBox searchDock;
    @FXML private TextField searchField;
    private Popup searchPopup = new Popup();
    private ListView<Object> searchResultsView = new ListView<>(FXCollections.observableArrayList());

    private final ProductSearchIndex searchIndex;
    private final ProductFlowService productFlowService;
    private final ProductService productService;
    private final SalesService salesService;
    private final WorkspaceManager workspaceManager;
    private Object selectedItem;

    public ProductFlowController(ProductFlowService productFlowService,
                                 ProductService productService,
                                 SalesService salesService,
                                 WorkspaceManager workspaceManager,
                                 ProductSearchIndex searchIndex) {
        this.productFlowService = productFlowService;
        this.productService = productService;
        this.salesService = salesService;
        this.workspaceManager = workspaceManager;
        this.searchIndex = searchIndex;
    }

    @FXML
    public void initialize() {
        setupFilters();
        setupTable();
        setupKeyboardShortcuts();
        loadData();
    }

    @FXML
    public void handleRefresh() {
        loadData();
    }

    private void setupFilters() {
        filterBar.setSearchVisible(false);
        filterBar.setResetInBottomRow(false);
        
        // 1. Setup Top Row: [ Search Dock | Analyze By | Reset ]
        selectionTypeCombo = filterBar.addFilter("analyzeBy", "Analyze By");
        selectionTypeCombo.getItems().addAll("Product", "Variant");
        selectionTypeCombo.setValue("Product");
        
        HBox topRow = filterBar.getTopRow();
        // Move Translate UI components to Top Row while preserving Right Actions/Spacer
        filterBar.getBottomRow().getChildren().remove(selectionTypeCombo);
        
        // Remove standard components we want to replace/reorder
        topRow.getChildren().remove(searchDock); // in case it was already there
        topRow.getChildren().remove(selectionTypeCombo);
        
        filterBar.setResetInBottomRow(false); 
        // topRow now has: [originalSearchField (hidden), resetButton, spacer, rightActions]
        
        // Final Arrangement: [searchDock, selectionTypeCombo, resetButton, spacer, rightActions]
        topRow.getChildren().add(0, searchDock);
        topRow.getChildren().add(1, selectionTypeCombo);
        // resetButton is already at index 2 (was index 1 before our adds)
        
        // 2. Setup Bottom Row: [ From Date | To Date | Event Type ]
        startDatePicker = filterBar.addDateFilter("startDate", "From Date");
        endDatePicker = filterBar.addDateFilter("endDate", "To Date");
        
        eventTypeFilter = filterBar.addMultiSelectFilter("eventTypes", "Event Type",
            List.of("SALE", "PURCHASE", "RETURN", "ADJUSTMENT"),
            s -> s
        );

        setupSearchAutocomplete();

        selectionTypeCombo.setOnAction(e -> {
            selectedItem = null;
            searchField.clear();
            searchPopup.hide();
            clearData();
        });

        filterBar.setOnFilterChange(filters -> loadData());
        paginationBar.setOnPageChange((page, size) -> loadData());
    }



    private void setupTable() {
        flowTable.setEmptyIcon("⌕");
        flowTable.setEmptyIconStyle("-fx-font-size: 48px; -fx-opacity: 0.4; -fx-text-fill: -color-text-muted; -fx-font-family: 'Segoe UI Symbol';");
        flowTable.setEmptyMessage("Select a product to analyze its flow");
        flowTable.setEmptySubtitle("Search for a product or variant above to monitor its stock history and performance.");

        TableColumn<ProductFlow, String> billIdCol = new TableColumn<>("Bill ID");
        billIdCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().shortBillRefNumber() != null && !cellData.getValue().shortBillRefNumber().isEmpty() 
            ? cellData.getValue().shortBillRefNumber() : "-"
        ));
        billIdCol.setPrefWidth(120);
        billIdCol.setCellFactory(col -> new TableCell<ProductFlow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "-".equals(item)) {
                    setText(item);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(8);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    
                    Label label = new Label(item);
                    label.setStyle("-fx-font-family: -font-family-mono; -fx-font-weight: bold;");
                    
                    Button viewBtn = new Button();
                    FontIcon viewIcon = new FontIcon("bx-show-alt");
                    viewIcon.setIconSize(14);
                    viewBtn.setGraphic(viewIcon);
                    viewBtn.getStyleClass().add("btn-edit-stock");
                    viewBtn.setTooltip(new Tooltip("View Details"));
                    
                    ProductFlow flow = getTableView().getItems().get(getIndex());
                    viewBtn.setOnAction(e -> handleViewBill(flow));
                    
                    container.getChildren().addAll(label, viewBtn);
                    setGraphic(container);
                    setText(null);
                }
            }
        });

        TableColumn<ProductFlow, String> customerCol = new TableColumn<>("Customer");
        customerCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().customerName() != null ? cellData.getValue().customerName() : "-"
        ));
        customerCol.setPrefWidth(140);

        TableColumn<ProductFlow, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().productName()));
        productCol.setPrefWidth(160);

        TableColumn<ProductFlow, String> variantCol = new TableColumn<>("Variant");
        variantCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().variantName()));
        variantCol.setPrefWidth(140);

        TableColumn<ProductFlow, Integer> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().quantity()));
        quantityCol.setCellFactory(column -> new TableCell<ProductFlow, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item > 0 ? "+" + item : String.valueOf(item));
                    setStyle("-fx-text-fill: " + (item > 0 ? "#10b981" : "#ef4444") + "; -fx-font-weight: bold;");
                    setAlignment(javafx.geometry.Pos.CENTER);
                }
            }
        });

        TableColumn<ProductFlow, String> typeCol = new TableColumn<>("Event Type");
        typeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().eventType()));
        typeCol.setPrefWidth(120);
        typeCol.setCellFactory(column -> new TableCell<ProductFlow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                    setGraphic(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createFlowTypeBadge(item));
                    setText(null);
                }
            }
        });

        TableColumn<ProductFlow, LocalDateTime> dateCol = new TableColumn<>("Date & Time");
        dateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().eventDate()));
        dateCol.setPrefWidth(180);
        dateCol.setCellFactory(column -> new TableCell<ProductFlow, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(TimeUtil.formatStandard(TimeUtil.toLocal(item)));
                }
            }
        });

        flowTable.getTableView().getColumns().setAll(List.of(
            billIdCol, customerCol, productCol, variantCol, quantityCol, typeCol, dateCol
        ));
    }


    private void loadData() {
        if (selectedItem == null) {
            clearData();
            return;
        }

        flowTable.setLoading(true);
        Platform.runLater(() -> {
            try {
                String type = selectionTypeCombo.getValue();
                LocalDate start = startDatePicker.getValue();
                LocalDate end = endDatePicker.getValue();
                
                String startStr = start != null ? start.atStartOfDay().toString() : null;
                String endStr = end != null ? end.atTime(23, 59, 59).toString() : null;

                List<String> eventTypes = eventTypeFilter.getSelectedItems();
                List<ProductFlow> flow;
                Map<String, Object> summary;
                
                if ("Product".equals(type)) {
                    Product p = (Product) selectedItem;
                    flow = productFlowService.getProductTimeline(p.id(), paginationBar.getPageSize(), paginationBar.getCurrentPage() * paginationBar.getPageSize(), startStr, endStr, eventTypes);
                    summary = productFlowService.getProductFlowSummary(p.id());
                } else {
                    Variant v = (Variant) selectedItem;
                    flow = productFlowService.getVariantTimeline(v.id(), paginationBar.getPageSize(), paginationBar.getCurrentPage() * paginationBar.getPageSize(), startStr, endStr, eventTypes);
                    summary = productFlowService.getVariantFlowSummary(v.id());
                }

                flowTable.setItems(FXCollections.observableArrayList(flow));
                // For now, if we have results, assume there might be more or just show actual count
                paginationBar.setTotalItems(flow.size() < paginationBar.getPageSize() ? (paginationBar.getCurrentPage() * paginationBar.getPageSize() + flow.size()) : 1000); 
                
                updateSummary(summary);
                flowTable.setLoading(false);
            } catch (Exception e) {
                flowTable.setLoading(false);
                com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load flow data", e);
                NotificationService.error("Failed to load flow data: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
            }
        });
    }

    private void setupSearchAutocomplete() {
        searchResultsView.setPrefWidth(500);
        searchResultsView.setPrefHeight(300);
        searchResultsView.getStyleClass().add("search-results-list");
        applyPopupListStyles(searchResultsView);

        searchPopup.getContent().add(searchResultsView);
        searchPopup.setAutoHide(true);

        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    VBox box = new VBox(2);
                    box.getStyleClass().add("search-item-box");
                    Label nameLabel = new Label();
                    nameLabel.getStyleClass().add("search-item-name");
                    Label detailsLabel = new Label();
                    detailsLabel.getStyleClass().add("search-item-details");

                    if (item instanceof Product p) {
                        nameLabel.setText(p.name());
                        detailsLabel.setText("Product - ID: " + p.id());
                    } else if (item instanceof Variant v) {
                        nameLabel.setText(v.productName() + (v.name().equalsIgnoreCase("Standard") ? "" : " - " + v.name()));
                        detailsLabel.setText("Variant - SKU: " + v.sku() + " | Price: " + v.price());
                    }

                    box.getChildren().addAll(nameLabel, detailsLabel);
                    setGraphic(box);
                }
            }
        });

        searchField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.trim().isEmpty()) {
                searchPopup.hide();
                return;
            }
            showAutocompletePopup(val.trim());
        });

        searchField.setOnMouseClicked(e -> {
            String query = searchField.getText().trim();
            if (!query.isEmpty()) showAutocompletePopup(query);
        });

        searchResultsView.setOnMouseClicked(e -> {
            Object selected = searchResultsView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectItem(selected);
            }
        });

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DOWN && searchPopup.isShowing()) {
                searchResultsView.requestFocus();
                searchResultsView.getSelectionModel().selectFirst();
            }
        });

        searchResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Object selected = searchResultsView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItem(selected);
                }
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                searchPopup.hide();
                searchField.requestFocus();
            }
        });
    }

    private void showAutocompletePopup(String query) {
        String type = selectionTypeCombo.getValue();
        List<Variant> variants = searchIndex.searchByName(query);
        
        List<Object> results = new java.util.ArrayList<>();
        if ("Product".equals(type)) {
            // Group variants by product and map to Product objects
            java.util.Set<Long> productIds = new java.util.HashSet<>();
            for (Variant v : variants) {
                if (productIds.add(v.productId())) {
                    try {
                        Product p = productService.getProductById(v.productId());
                        if (p != null) results.add(p);
                    } catch (Exception e) {}
                }
            }
        } else {
            results.addAll(variants);
        }

        if (results.isEmpty()) {
            searchPopup.hide();
            return;
        }

        searchResultsView.getItems().setAll(results);
        javafx.geometry.Point2D pos = searchField.localToScreen(0, searchField.getHeight());
        if (pos != null) {
            searchPopup.show(searchField, pos.getX(), pos.getY());
        }
    }

    private void selectItem(Object item) {
        this.selectedItem = item;
        if (item instanceof Product p) {
            searchField.setText(p.name());
        } else if (item instanceof Variant v) {
            searchField.setText(v.productName() + (v.name().equalsIgnoreCase("Standard") ? "" : " - " + v.name()));
        }
        searchPopup.hide();
        loadData();
    }

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (root == null || root.getScene() == null) {
                return;
            }
            root.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if ((e.isControlDown() || e.isMetaDown()) && e.getCode() == KeyCode.K) {
                    searchField.requestFocus();
                    e.consume();
                }
            });
        });
    }



    private void updateSummary(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            clearSummary();
            return;
        }
        totalSoldLabel.setText(String.valueOf(summary.getOrDefault("totalSold", 0)));
        totalPurchasedLabel.setText(String.valueOf(summary.getOrDefault("totalPurchased", 0)));
        totalReturnedLabel.setText(String.valueOf(summary.getOrDefault("totalReturned", 0)));
        netMovementLabel.setText(String.valueOf(summary.getOrDefault("netMovement", 0)));
        
        int net = (int) summary.getOrDefault("netMovement", 0);
        netMovementLabel.setStyle("-fx-text-fill: " + (net >= 0 ? "#2563eb" : "#ef4444") + ";");
    }

    private void applyPopupListStyles(ListView<?> listView) {
        String stylesheet = Objects.requireNonNull(
                getClass().getResource("/styles/views/pos.css"),
                "Missing stylesheet: /styles/views/pos.css").toExternalForm();
        if (!listView.getStylesheets().contains(stylesheet)) {
            listView.getStylesheets().add(stylesheet);
        }
    }

    private void clearData() {
        flowTable.setItems(FXCollections.observableArrayList());
        clearSummary();
        paginationBar.setTotalItems(0);
    }

    private void clearSummary() {
        totalSoldLabel.setText("0");
        totalPurchasedLabel.setText("0");
        totalReturnedLabel.setText("0");
        netMovementLabel.setText("0");
        netMovementLabel.setStyle("");
    }
    private void handleViewBill(ProductFlow flow) {
        if (flow.billRefId() == null) return;
        
        try {
            long billId = flow.billRefId();
            if ("sale_item".equalsIgnoreCase(flow.referenceType()) || "sale".equalsIgnoreCase(flow.eventType())) {
                SaleResponse saleResponse = salesService.getSaleDetails(billId);
                Sale sale = saleResponse.sale();
                if (sale != null) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("sale", sale);
                    workspaceManager.openOrFocusWindow("Bill: " + sale.invoiceNumber(), "/fxml/sales/sale-detail-view.fxml", params);
                }
            } else if ("purchase_order".equalsIgnoreCase(flow.referenceType()) || "purchase".equalsIgnoreCase(flow.eventType())) {
                Map<String, Object> params = new HashMap<>();
                params.put("id", billId);
                workspaceManager.openOrFocusWindow("PO: " + flow.billRefNumber(), "/fxml/purchase/purchase-order-detail.fxml", params);
            }
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Could not open document details", e);
            NotificationService.error("Could not open document details: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }
}

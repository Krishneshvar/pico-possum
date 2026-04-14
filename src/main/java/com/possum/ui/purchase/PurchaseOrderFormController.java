package com.possum.ui.purchase;

import com.possum.application.auth.AuthContext;
import com.possum.application.purchase.PurchaseService;
import com.possum.application.sales.SalesService;
import com.possum.domain.model.PaymentMethod;
import com.possum.domain.model.PurchaseOrder;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.domain.model.Supplier;
import com.possum.domain.model.Variant;
import com.possum.domain.repositories.SupplierRepository;
import com.possum.domain.repositories.VariantRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.DialogStyler;
import com.possum.ui.common.ErrorHandler;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.ui.navigation.Parameterizable;
import com.possum.ui.sales.ProductSearchIndex;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;

import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.util.StringConverter;


import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.possum.ui.purchase.cells.*;

public class PurchaseOrderFormController implements Parameterizable, PurchaseCellHandler {

    @FXML private Label titleLabel;
    @FXML private ComboBox<Supplier> supplierCombo;
    @FXML private ComboBox<PaymentMethod> paymentMethodCombo;
    @FXML private TextField searchVariantField;
    @FXML private DataTableView<PurchaseItemRow> itemsTable;
    @FXML private Button saveButton;
    @FXML private Label totalCostLabel;
    @FXML private Label itemCountLabel;

    private PurchaseService purchaseService;
    private SupplierRepository supplierRepository;
    private VariantRepository variantRepository;
    private WorkspaceManager workspaceManager;
    private ProductSearchIndex searchIndex;
    private SalesService salesService;

    private Popup searchPopup = new Popup();
    private ListView<Variant> searchResultsView = new ListView<>();

    private PurchaseOrder existingPo;
    private Runnable onSaveCallback;
    private ObservableList<PurchaseItemRow> itemRows = FXCollections.observableArrayList();
    private boolean isViewMode = false;

    public PurchaseOrderFormController(PurchaseService purchaseService, SupplierRepository supplierRepository,
                                       VariantRepository variantRepository, WorkspaceManager workspaceManager,
                                       ProductSearchIndex searchIndex, SalesService salesService) {
        this.purchaseService = purchaseService;
        this.supplierRepository = supplierRepository;
        this.variantRepository = variantRepository;
        this.workspaceManager = workspaceManager;
        this.searchIndex = searchIndex;
        this.salesService = salesService;
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        boolean isView = false;
        if (params != null) {
            isView = "view".equals(params.get("mode"));
            if (params.containsKey("order")) {
                existingPo = (PurchaseOrder) params.get("order");
                final boolean finalIsView = isView;
                Platform.runLater(() -> titleLabel.setText(finalIsView ? "View Purchase Order" : "Edit Purchase Order"));
            } else {
                existingPo = null;
                Platform.runLater(() -> titleLabel.setText("Create Purchase Order"));
            }
            if (params.containsKey("onSave")) {
                onSaveCallback = (Runnable) params.get("onSave");
            }
        } else {
            existingPo = null;
            Platform.runLater(() -> titleLabel.setText("Create Purchase Order"));
        }
        final boolean finalIsView = isView;
        Platform.runLater(() -> {
            if (finalIsView) {
                saveButton.setVisible(false);
                saveButton.setManaged(false);
                if (searchVariantField != null) {
                    searchVariantField.setVisible(false);
                    searchVariantField.setManaged(false);
                }
                supplierCombo.setDisable(true);
                paymentMethodCombo.setDisable(true);
            }
        });
        loadData(isView);
    }

    @FXML
    public void initialize() {
        setupItemsTable();
        setupVariantSearch();
        setupSearchAutocomplete();
    }

    private void setupItemsTable() {
        TableColumn<PurchaseItemRow, String> productCol = new TableColumn<>("Product | Variant");
        productCol.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        productCol.setPrefWidth(300);
        productCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    PurchaseItemRow row = getTableView().getItems().get(getIndex());
                    VBox vbox = new VBox(2);
                    Label productLabel = new Label(row.getProductName());
                    productLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                    Label variantLabel = new Label(row.getSku() + " (" + row.getVariantName() + ")");
                    variantLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");
                    vbox.getChildren().addAll(productLabel, variantLabel);
                    setGraphic(vbox);
                }
            }
        });
        
        TableColumn<PurchaseItemRow, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setPrefWidth(80);
        qtyCol.setStyle("-fx-alignment: CENTER;");
        qtyCol.setCellFactory(col -> new PurchaseQtyCell(this));
        
        TableColumn<PurchaseItemRow, BigDecimal> costCol = new TableColumn<>("Unit Cost");
        costCol.setCellValueFactory(cellData -> cellData.getValue().unitCostProperty());
        costCol.setPrefWidth(100);
        costCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        costCol.setCellFactory(col -> new PurchaseCostCell(this));
        
        TableColumn<PurchaseItemRow, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cellData -> cellData.getValue().totalProperty());
        totalCol.setPrefWidth(100);
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(item));
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1976d2;");
                }
            }
        });
        
        TableColumn<PurchaseItemRow, Void> actionCol = new TableColumn<>("Delete");
        actionCol.setPrefWidth(60);
        actionCol.setStyle("-fx-alignment: CENTER;");
        actionCol.setCellFactory(col -> new PurchaseActionCell(this));
        
        itemsTable.getTableView().getColumns().setAll(List.of(productCol, qtyCol, costCol, totalCol, actionCol));
        itemsTable.setEmptyMessage("No items added yet");
        itemsTable.setEmptySubtitle("Search and add products above to build your order.");
        itemsTable.getTableView().setItems(itemRows);
    }

    private void setupVariantSearch() {
        if (searchVariantField == null) return;
        searchVariantField.textProperty().addListener((obs, oldVal, newVal) -> showAutocompletePopup(newVal != null ? newVal.trim() : ""));
        searchVariantField.focusedProperty().addListener((obs, old, isFocused) -> {
            if (isFocused) showAutocompletePopup(searchVariantField.getText().trim());
            else Platform.runLater(() -> { if (!searchResultsView.isFocused()) searchPopup.hide(); });
        });
        searchVariantField.setOnMouseClicked(e -> showAutocompletePopup(searchVariantField.getText().trim()));
        searchVariantField.setOnAction(e -> handleAddItem());
    }

    private void setupSearchAutocomplete() {
        searchResultsView.getStyleClass().add("search-results-list");
        applyPopupListStyles(searchResultsView);
        searchPopup.getContent().add(searchResultsView);
        searchPopup.setAutoHide(true);

        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Variant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox box = new VBox(2);
                    box.getStyleClass().add("search-item-box");
                    Label name = new Label(item.productName() + (item.name().equals("Default") ? "" : " - " + item.name()));
                    name.getStyleClass().add("search-item-name");
                    Label details = new Label(item.sku() + " • Cost: " + CurrencyUtil.format(item.costPrice() != null ? item.costPrice() : BigDecimal.ZERO) + " • Stock: " + (item.stock() != null ? item.stock() : "∞"));
                    details.getStyleClass().add("search-item-details");
                    box.getChildren().addAll(name, details);
                    setGraphic(box);
                }
            }
        });

        searchResultsView.setOnMouseClicked(e -> {
            Variant v = searchResultsView.getSelectionModel().getSelectedItem();
            if (v != null) { addVariantToCart(v); searchVariantField.clear(); searchPopup.hide(); }
        });

        searchResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Variant v = searchResultsView.getSelectionModel().getSelectedItem();
                if (v != null) { addVariantToCart(v); searchVariantField.clear(); searchPopup.hide(); }
            } else if (e.getCode() == javafx.scene.input.KeyCode.UP && searchResultsView.getSelectionModel().getSelectedIndex() <= 0) {
                searchVariantField.requestFocus();
            }
        });

        searchVariantField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DOWN && searchPopup.isShowing()) {
                searchResultsView.requestFocus();
                searchResultsView.getSelectionModel().selectFirst();
            }
        });
    }

    private void showAutocompletePopup(String query) {
        List<Variant> results = searchIndex.searchByName(query);
        if (!results.isEmpty()) {
            searchResultsView.setItems(FXCollections.observableArrayList(results));
            searchResultsView.setPrefHeight(Math.min(results.size() * 52 + 5, 300));
            searchResultsView.setPrefWidth(Math.max(searchVariantField.getWidth(), 350));
            javafx.geometry.Point2D pos = searchVariantField.localToScreen(0, searchVariantField.getHeight() + 2);
            if (pos != null) searchPopup.show(searchVariantField, pos.getX(), pos.getY());
        } else {
            searchPopup.hide();
        }
    }

    @FXML
    private void handleAddItem() {
        String query = searchVariantField.getText().trim();
        if (query.isEmpty()) { NotificationService.warning("Enter a product name or SKU to search"); return; }

        if (searchPopup.isShowing() && !searchResultsView.getItems().isEmpty()) {
            Variant selected = searchResultsView.getSelectionModel().getSelectedItem();
            if (selected == null) selected = searchResultsView.getItems().get(0);
            addVariantToCart(selected);
            searchVariantField.clear();
            searchPopup.hide();
            return;
        }

        try {
            List<Variant> results = searchIndex.searchByName(query);
            if (results.isEmpty()) { NotificationService.warning("No product found matching: " + query); return; }
            if (results.size() == 1) { addVariantToCart(results.get(0)); searchVariantField.clear(); }
            else showVariantSelectionDialog(results);
        } catch (Exception ex) { NotificationService.error("Failed to search products"); }
    }

    private void showVariantSelectionDialog(List<Variant> variants) {
        Dialog<Variant> dialog = new Dialog<>();
        DialogStyler.apply(dialog);
        dialog.setTitle("Select Product Variant");
        dialog.setHeaderText("Multiple products found. Select one:");
        ListView<Variant> listView = new ListView<>(FXCollections.observableArrayList(variants));
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Variant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.productName() + " - " + item.name() + " (" + item.sku() + ") - " + CurrencyUtil.format(item.costPrice() != null ? item.costPrice() : BigDecimal.ZERO));
            }
        });
        dialog.getDialogPane().setContent(listView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == ButtonType.OK ? listView.getSelectionModel().getSelectedItem() : null);
        dialog.showAndWait().ifPresent(v -> { addVariantToCart(v); searchVariantField.clear(); });
    }

    private void loadData(boolean isView) {
        this.isViewMode = isView;
        Platform.runLater(() -> {
            try {
                PagedResult<Supplier> suppliers = supplierRepository.getAllSuppliers(new com.possum.shared.dto.SupplierFilter(0, 1000, null, null, "name", "ASC"));
                makeSupplierComboSearchable(suppliers.items());
                List<PaymentMethod> paymentMethods = salesService.getPaymentMethods();
                makePaymentMethodComboSearchable(paymentMethods);

                if (existingPo != null) {
                    suppliers.items().stream().filter(s -> s.id().equals(existingPo.supplierId())).findFirst().ifPresent(supplierCombo::setValue);
                    if (existingPo.paymentMethodId() != null) {
                        paymentMethods.stream().filter(pm -> pm.id().equals(existingPo.paymentMethodId())).findFirst().ifPresent(paymentMethodCombo::setValue);
                    }
                    PurchaseService.PurchaseOrderDetail detail = purchaseService.getPurchaseOrderById(existingPo.id());
                    for (PurchaseOrderItem item : detail.items()) {
                        variantRepository.findVariantByIdSync(item.variantId()).ifPresent(v -> {
                            PurchaseItemRow row = new PurchaseItemRow(v);
                            row.setQuantity(item.quantity());
                            row.setUnitCost(item.unitCost());
                            itemRows.add(row);
                        });
                    }
                }
                recalculateTotal();
            } catch (Exception e) { NotificationService.error("Failed to load data for PO Form"); }
        });
    }

    private void makePaymentMethodComboSearchable(List<PaymentMethod> allMethods) {
        paymentMethodCombo.setItems(FXCollections.observableArrayList(allMethods));
        paymentMethodCombo.setConverter(new StringConverter<>() {
            @Override public String toString(PaymentMethod m) { return m == null ? "" : m.name(); }
            @Override public PaymentMethod fromString(String s) { return allMethods.stream().filter(m -> m.name().equals(s)).findFirst().orElse(null); }
        });
        if (existingPo == null) allMethods.stream().filter(pm -> "Cash".equalsIgnoreCase(pm.name())).findFirst().ifPresent(paymentMethodCombo::setValue);
    }

    private void makeSupplierComboSearchable(List<Supplier> allSuppliers) {
        supplierCombo.setItems(FXCollections.observableArrayList(allSuppliers));
        supplierCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Supplier s) { return s == null ? "" : s.name(); }
            @Override public Supplier fromString(String query) { return allSuppliers.stream().filter(sup -> sup.name().equals(query)).findFirst().orElse(null); }
        });
    }

    public void recalculateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        int totalQty = 0;
        for (PurchaseItemRow row : itemRows) {
            total = total.add(row.getUnitCost().multiply(BigDecimal.valueOf(row.getQuantity())));
            totalQty += row.getQuantity();
        }
        final BigDecimal finalTotal = total;
        final int finalQty = totalQty;
        Platform.runLater(() -> {
            if (totalCostLabel != null) totalCostLabel.setText(CurrencyUtil.format(finalTotal));
            if (itemCountLabel != null) itemCountLabel.setText(itemRows.size() + " item" + (itemRows.size() != 1 ? "s" : "") + " (" + finalQty + " units)");
        });
    }

    private void addVariantToCart(Variant variant) {
        for (PurchaseItemRow row : itemRows) {
            if (row.getVariantId() != null && row.getVariantId().equals(variant.id())) {
                row.setQuantity(row.getQuantity() + 1);
                NotificationService.success("Quantity updated for " + variant.name());
                recalculateTotal();
                return;
            }
        }
        itemRows.add(new PurchaseItemRow(variant));
        NotificationService.success("Added " + variant.name() + " to order");
        recalculateTotal();
    }

    @FXML private void handleCancel() { workspaceManager.closeActiveWindow(); }

    @FXML
    private void handleSave() {
        try {
            Supplier supplier = supplierCombo.getValue();
            if (supplier == null) { NotificationService.error("Select a supplier"); return; }
            List<PurchaseService.PurchaseOrderItemRequest> items = itemRows.stream()
                .filter(row -> row.getVariantId() != null)
                .map(row -> new PurchaseService.PurchaseOrderItemRequest(row.getVariantId(), row.getQuantity(), row.getUnitCost()))
                .toList();
            if (items.isEmpty()) { NotificationService.error("Add at least one valid item"); return; }
            PaymentMethod pm = paymentMethodCombo.getValue();
            if (pm == null) { NotificationService.error("Select a payment method"); return; }

            long userId = AuthContext.getCurrentUser().id();
            if (existingPo == null) purchaseService.createPurchaseOrder(supplier.id(), pm.id(), userId, items);
            else purchaseService.updatePurchaseOrder(existingPo.id(), supplier.id(), pm.id(), userId, items);
            
            NotificationService.success("Purchase order saved");
            if (onSaveCallback != null) onSaveCallback.run();
            workspaceManager.closeActiveWindow();
        } catch (Exception e) { 
            LoggingConfig.getLogger().error("Failed to save PO", e);
            NotificationService.error("Failed to save Purchase Order: " + ErrorHandler.toUserMessage(e)); 
        }
    }

    public boolean isViewMode() { return isViewMode; }
    
    public void removeRow(PurchaseItemRow row) {
        if (row != null) itemRows.remove(row);
    }

    private void applyPopupListStyles(ListView<?> lv) {
        String s = Objects.requireNonNull(getClass().getResource("/styles/pos.css")).toExternalForm();
        if (!lv.getStylesheets().contains(s)) lv.getStylesheets().add(s);
    }
}

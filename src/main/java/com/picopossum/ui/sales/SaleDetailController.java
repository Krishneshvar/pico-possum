package com.picopossum.ui.sales;

import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.dto.SaleResponse;
import com.picopossum.domain.model.Product;
import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.domain.model.Return;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.infrastructure.printing.BillRenderer;
import com.picopossum.infrastructure.printing.PrintOutcome;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.ui.navigation.Parameterizable;
import com.picopossum.ui.workspace.WorkspaceManager;
import com.picopossum.ui.common.controls.DataTableView;
import com.picopossum.ui.common.controls.NotificationService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import com.picopossum.shared.util.TimeUtil;
import com.picopossum.shared.util.CurrencyUtil;
import com.picopossum.application.sales.dto.UpdateSaleItemRequest;
import javafx.application.Platform;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SaleDetailController implements Parameterizable {

    @FXML private Label invoiceLabel;
    @FXML private Label statusBadge;
    @FXML private Label dateLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label customerContactLabel;
    @FXML private Label paymentMethodLabel;
    @FXML private Label billerLabel;
    
    @FXML private DataTableView<SaleItem> itemsTable;
    @FXML private VBox returnedItemsContainer;
    @FXML private DataTableView<SaleItem> returnedItemsTable;
    
    @FXML private Label subtotalLabel;
    @FXML private Label discountTotalLabel;
    @FXML private Label grandTotalLabel;
    @FXML private Label paidAmountLabel;
    @FXML private Label balanceTypeLabel;
    @FXML private Label balanceAmountLabel;
    @FXML private javafx.scene.control.Button editButton;
    @FXML private javafx.scene.control.Button createReturnButton;

    @FXML private javafx.scene.layout.HBox editActionsDock;
    @FXML private javafx.scene.layout.VBox customerViewBox;
    @FXML private javafx.scene.layout.VBox customerEditBox;
    @FXML private javafx.scene.layout.VBox paymentViewBox;
    @FXML private javafx.scene.layout.VBox paymentEditBox;
    @FXML private javafx.scene.control.ComboBox<com.picopossum.domain.model.Customer> customerCombo;
    @FXML private javafx.scene.control.ComboBox<com.picopossum.domain.model.PaymentMethod> paymentMethodCombo;

    @FXML private javafx.scene.layout.HBox addItemDock;
    @FXML private javafx.scene.control.TextField itemSearchField;
    @FXML private javafx.scene.layout.HBox draftTotalsDock;
    @FXML private Label draftSubtotalLabel;
    @FXML private Label draftTotalLabel;
    @FXML private Label refundTotalLabel;
    @FXML private javafx.scene.layout.HBox refundSummaryRow;

    private final javafx.collections.ObservableList<SaleItem> editingItems = FXCollections.observableArrayList();
    private boolean isEditingMode = false;
    
    private final SalesService salesService;
    private final WorkspaceManager workspaceManager;
    private final SettingsStore settingsStore;
    private final PrinterService printerService;
    private final ProductSearchIndex searchIndex;

    private SaleDetailTableManager tableManager;
    private SaleDetailSearchHandler searchHandler;

    private Sale currentSale;
    private SaleResponse saleDetails;

    public SaleDetailController(SalesService salesService, 
                                WorkspaceManager workspaceManager,
                                SettingsStore settingsStore,
                                PrinterService printerService,
                                ProductSearchIndex searchIndex) {
        this.salesService = salesService;
        this.workspaceManager = workspaceManager;
        this.settingsStore = settingsStore;
        this.printerService = printerService;
        this.searchIndex = searchIndex;
    }

    @FXML
    public void initialize() {

        tableManager = new SaleDetailTableManager(itemsTable, returnedItemsTable, this::calculateDraftTotals);
        tableManager.setupActiveItemsTable();
        tableManager.setupReturnedItemsTable();

        searchHandler = new SaleDetailSearchHandler(itemSearchField, searchIndex, this::addProductToEditingItems);
        searchHandler.setup();

        setupEditControls();
    }

    private void setupEditControls() {
        customerCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.picopossum.domain.model.Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + (item.phone() != null ? " (" + item.phone() + ")" : ""));
            }
        });
        customerCombo.setButtonCell(customerCombo.getCellFactory().call(null));

        paymentMethodCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.picopossum.domain.model.PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        paymentMethodCombo.setButtonCell(paymentMethodCombo.getCellFactory().call(null));
    }

    private void calculateDraftTotals() {
        BigDecimal subtotal = editingItems.stream()
                .map(i -> i.pricePerUnit().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal discounts = editingItems.stream()
                .map(i -> i.discountAmount() != null ? i.discountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        draftTotalLabel.setText(CurrencyUtil.format(subtotal.subtract(discounts)));
    }

    private void addProductToEditingItems(Product product) {
        editingItems.stream()
            .filter(i -> i.productId().equals(product.id()))
            .findFirst()
            .ifPresentOrElse(
                existing -> {
                    int idx = editingItems.indexOf(existing);
                    editingItems.set(idx, new SaleItem(
                        existing.id(), existing.saleId(), existing.productId(),
                        existing.sku(), existing.productName(), existing.quantity() + 1, existing.pricePerUnit(),
                        existing.costPerUnit(), existing.discountAmount(), existing.returnedQuantity()
                    ));
                },
                () -> {
                    editingItems.add(new SaleItem(
                        null, currentSale.id(), product.id(),
                        product.sku(), product.name(), 
                        1, product.mrp(), product.costPrice(), 
                        BigDecimal.ZERO, 0
                    ));
                }
            );
        calculateDraftTotals();
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params != null && params.containsKey("sale")) {
            this.currentSale = (Sale) params.get("sale");
            loadSaleDetails();
            
            // Check if we should start in edit mode
            if (Boolean.TRUE.equals(params.get("editing"))) {
                Platform.runLater(this::toggleEditMode);
            }
        }
    }

    private void loadSaleDetails() {
        try {
            this.saleDetails = salesService.getSaleDetails(currentSale.id());
            this.currentSale = saleDetails.sale(); // Update with latest info from service
            
            String displayId = (currentSale.invoiceId() != null && !currentSale.invoiceId().isBlank()) ? currentSale.invoiceId() : currentSale.invoiceNumber();
            invoiceLabel.setText("#" + displayId);
            statusBadge.setText(currentSale.status().replace("_", " ").toUpperCase());
            applyStatusStyle(currentSale.status());
            
            dateLabel.setText("Processed on " + TimeUtil.formatStandard(TimeUtil.toLocal(currentSale.saleDate())));
            customerNameLabel.setText(currentSale.customerName() != null ? currentSale.customerName() : "Walk-in Customer");
            customerContactLabel.setText(currentSale.customerPhone() != null ? currentSale.customerPhone() : "No contact info");
            
            billerLabel.setText("Biller: " + (currentSale.billerName() != null ? currentSale.billerName() : "System"));
            
            paymentMethodLabel.setText(currentSale.paymentMethodName() != null ? currentSale.paymentMethodName() : "N/A");

            java.util.List<SaleItem> activeItems = saleDetails.items().stream()
                .filter(i -> (i.quantity() - (i.returnedQuantity() != null ? i.returnedQuantity() : 0)) > 0)
                .toList();
            
            java.util.List<SaleItem> returnedItems = saleDetails.items().stream()
                .filter(i -> i.returnedQuantity() != null && i.returnedQuantity() > 0)
                .toList();

            tableManager.setItems(FXCollections.observableArrayList(activeItems), FXCollections.observableArrayList(returnedItems));
            returnedItemsContainer.setVisible(!returnedItems.isEmpty());
            returnedItemsContainer.setManaged(!returnedItems.isEmpty());
            
            updateSummary();
        } catch (Exception e) {
            e.printStackTrace();
            NotificationService.error("Failed to load sale details: " + e.getMessage());
        }
    }

    private void applyStatusStyle(String status) {
        statusBadge.getStyleClass().removeAll("badge-success", "badge-error", "badge-warning", "badge-neutral");
        statusBadge.getStyleClass().add("badge-status");
        if ("paid".equalsIgnoreCase(status)) {
            statusBadge.getStyleClass().add("badge-success");
        } else if ("cancelled".equalsIgnoreCase(status) || "refunded".equalsIgnoreCase(status)) {
            statusBadge.getStyleClass().add("badge-error");
        } else if ("partially_refunded".equalsIgnoreCase(status) || "partially_paid".equalsIgnoreCase(status) || "draft".equalsIgnoreCase(status)) {
            statusBadge.getStyleClass().add("badge-warning");
        } else {
            statusBadge.getStyleClass().add("badge-neutral");
        }
    }

    private void updateSummary() {
        BigDecimal subtotal = saleDetails.items().stream()
                .map(i -> {
                    BigDecimal price = i.pricePerUnit() != null ? i.pricePerUnit() : BigDecimal.ZERO;
                    BigDecimal qty = BigDecimal.valueOf(i.quantity() != null ? i.quantity() : 0);
                    return price.multiply(qty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal lineItemDiscount = saleDetails.items().stream()
                .map(i -> i.discountAmount() != null ? i.discountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal billDiscount = currentSale.discount() != null ? currentSale.discount() : BigDecimal.ZERO;
        BigDecimal totalDiscount = lineItemDiscount.add(billDiscount);
        
        BigDecimal grandTotal = currentSale.totalAmount() != null ? currentSale.totalAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount = currentSale.paidAmount() != null ? currentSale.paidAmount() : BigDecimal.ZERO;
        
        discountTotalLabel.setText("-" + CurrencyUtil.format(totalDiscount));
        
        BigDecimal totalRefunded = saleDetails.returns().stream()
                .map(Return::totalRefund)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        boolean hasRefund = totalRefunded.compareTo(BigDecimal.ZERO) > 0;
        if (refundSummaryRow != null) {
            refundSummaryRow.setVisible(hasRefund);
            refundSummaryRow.setManaged(hasRefund);
        }
        if (refundTotalLabel != null) {
            refundTotalLabel.setText("-" + CurrencyUtil.format(totalRefunded));
        }

        BigDecimal effectiveGrandTotal = grandTotal.subtract(totalRefunded).max(BigDecimal.ZERO);
        grandTotalLabel.setText(CurrencyUtil.format(effectiveGrandTotal));
        paidAmountLabel.setText(CurrencyUtil.format(paidAmount));
        
        BigDecimal balance = paidAmount.subtract(effectiveGrandTotal);
        
        if (balance.compareTo(BigDecimal.ZERO) >= 0) {
            balanceTypeLabel.setText("Change");
            balanceAmountLabel.setText(CurrencyUtil.format(balance));
            balanceAmountLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #16a34a;");
        } else {
            balanceTypeLabel.setText("Due Amount");
            balanceAmountLabel.setText(CurrencyUtil.format(balance.abs()));
            balanceAmountLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: #dc2626;");
        }
    }

    @FXML
    private void handlePrint() {
        try {
            com.picopossum.shared.dto.GeneralSettings generalSettings = settingsStore.loadGeneralSettings();
            com.picopossum.shared.dto.BillSettings billSettings = settingsStore.loadBillSettings();
            String billHtml = BillRenderer.renderBill(saleDetails, generalSettings, billSettings);

            printerService.printInvoiceDetailed(
                            billHtml,
                            generalSettings.getDefaultPrinterName(),
                            billSettings.getPaperWidth()
                    )
                    .thenAccept(this::notifyPrintOutcome)
                    .exceptionally(ex -> {
                        Platform.runLater(() -> NotificationService.error("Print error: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception ex) {
            NotificationService.error("Unable to print invoice: " + ex.getMessage());
        }
    }

    private void notifyPrintOutcome(PrintOutcome outcome) {
        Platform.runLater(() -> {
            if (outcome.success()) {
                String printer = outcome.printerName() != null ? outcome.printerName() : "configured printer";
                NotificationService.success("Invoice sent to " + printer);
            } else {
                NotificationService.warning("Print failed: " + outcome.message());
            }
        });
    }

    @FXML
    private void toggleEditMode() {
        isEditingMode = !isEditingMode;
        boolean editing = isEditingMode;
        
        tableManager.setEditingMode(editing);

        if (editing) {
            customerCombo.setItems(FXCollections.observableArrayList(salesService.getAllCustomers()));
            paymentMethodCombo.setItems(FXCollections.observableArrayList(salesService.getPaymentMethods()));
            
            long currentCustomerId = currentSale.customerId() != null ? currentSale.customerId() : -1L;
            customerCombo.getItems().stream()
                .filter(c -> c.id() == currentCustomerId)
                .findFirst()
                .ifPresent(customerCombo::setValue);
            
            long currentMethodId = currentSale.paymentMethodId() != null ? currentSale.paymentMethodId() : -1L;
                    
            paymentMethodCombo.getItems().stream()
                .filter(m -> m.id() == currentMethodId)
                .findFirst()
                .ifPresent(paymentMethodCombo::setValue);

            editingItems.setAll(saleDetails.items());
            itemsTable.getTableView().setItems(editingItems);
            calculateDraftTotals();
        } else {
            itemsTable.getTableView().setItems(FXCollections.observableArrayList(saleDetails.items()));
        }

        editButton.setVisible(!editing);
        editButton.setManaged(!editing);
        editActionsDock.setVisible(editing);
        editActionsDock.setManaged(editing);

        addItemDock.setVisible(editing);
        addItemDock.setManaged(editing);
        draftTotalsDock.setVisible(editing);
        draftTotalsDock.setManaged(editing);
        
        customerViewBox.setVisible(!editing);
        customerViewBox.setManaged(!editing);
        customerEditBox.setVisible(editing);
        customerEditBox.setManaged(editing);
        
        paymentViewBox.setVisible(!editing);
        paymentViewBox.setManaged(!editing);
        paymentEditBox.setVisible(editing);
        paymentEditBox.setManaged(editing);
    }

    @FXML
    private void handleSave() {
        try {
            com.picopossum.domain.model.Customer selectedCustomer = customerCombo.getValue();
            com.picopossum.domain.model.PaymentMethod selectedMethod = paymentMethodCombo.getValue();
            
            Long childCustomerId = selectedCustomer != null ? selectedCustomer.id() : null;
            long newMethodId = selectedMethod != null ? selectedMethod.id() : -1L;
            
            com.picopossum.application.auth.AuthUser currentUser = com.picopossum.application.auth.AuthContext.getCurrentUser();
            boolean changed = false;

            if (!java.util.Objects.equals(currentSale.customerId(), childCustomerId)) {
                salesService.changeSaleCustomer(currentSale.id(), childCustomerId, currentUser.id());
                changed = true;
            }

            long currentMethodId = currentSale.paymentMethodId() != null ? currentSale.paymentMethodId() : -1L;
            
            if (newMethodId != -1 && newMethodId != currentMethodId) {
                salesService.changeSalePaymentMethod(currentSale.id(), newMethodId, currentUser.id());
                changed = true;
            }

            List<UpdateSaleItemRequest> itemRequests = editingItems.stream()
                    .map(item -> new UpdateSaleItemRequest(
                            item.productId(), item.quantity(), item.pricePerUnit(), item.discountAmount()
                    )).toList();
            
            salesService.updateSaleItems(currentSale.id(), itemRequests, currentUser.id());
            changed = true;

            if (changed) {
                NotificationService.success("Bill updated successfully");
                loadSaleDetails();
            }
            toggleEditMode();
            
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to save sale details update: {}", e.getMessage(), e);
            NotificationService.error("Update failed: " + com.picopossum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    @FXML
    private void handleReturn() {
        Map<String, Object> params = Map.of("invoiceNumber", currentSale.invoiceNumber());
        workspaceManager.openDialog("Process Return", "/fxml/returns/create-return-dialog.fxml", params);
        loadSaleDetails();
    }
}

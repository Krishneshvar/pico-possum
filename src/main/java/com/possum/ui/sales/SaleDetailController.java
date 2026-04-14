package com.possum.ui.sales;

import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.infrastructure.printing.BillRenderer;
import com.possum.infrastructure.printing.PrintOutcome;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.ui.navigation.Parameterizable;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import com.possum.shared.util.TimeUtil;
import com.possum.shared.util.CurrencyUtil;
import com.possum.application.sales.dto.UpdateSaleItemRequest;
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
    @FXML private Label taxTotalLabel;
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
    @FXML private javafx.scene.control.ComboBox<com.possum.domain.model.Customer> customerCombo;
    @FXML private javafx.scene.control.ComboBox<com.possum.domain.model.PaymentMethod> paymentMethodCombo;

    @FXML private javafx.scene.layout.HBox addItemDock;
    @FXML private javafx.scene.control.TextField itemSearchField;
    @FXML private javafx.scene.layout.HBox draftTotalsDock;
    @FXML private Label draftSubtotalLabel;
    @FXML private Label draftTaxLabel;
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
        if (editButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(editButton, com.possum.application.auth.Permissions.SALES_MANAGE);
        }
        if (createReturnButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(createReturnButton, com.possum.application.auth.Permissions.SALES_REFUND);
        }

        tableManager = new SaleDetailTableManager(itemsTable, returnedItemsTable, this::calculateDraftTotals);
        tableManager.setupActiveItemsTable();
        tableManager.setupReturnedItemsTable();

        searchHandler = new SaleDetailSearchHandler(itemSearchField, searchIndex, this::addVariantToEditingItems);
        searchHandler.setup();

        setupEditControls();
    }

    private void setupEditControls() {
        customerCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.possum.domain.model.Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + (item.phone() != null ? " (" + item.phone() + ")" : ""));
            }
        });
        customerCombo.setButtonCell(customerCombo.getCellFactory().call(null));

        paymentMethodCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(com.possum.domain.model.PaymentMethod item, boolean empty) {
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
        
        BigDecimal taxEstimate = editingItems.stream()
                .map(i -> i.taxAmount() != null ? i.taxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal discounts = editingItems.stream()
                .map(i -> i.discountAmount() != null ? i.discountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        draftSubtotalLabel.setText(CurrencyUtil.format(subtotal));
        draftTaxLabel.setText(CurrencyUtil.format(taxEstimate));
        draftTotalLabel.setText(CurrencyUtil.format(subtotal.add(taxEstimate).subtract(discounts)));
    }

    private void addVariantToEditingItems(com.possum.domain.model.Variant variant) {
        editingItems.stream()
            .filter(i -> i.variantId() == variant.id())
            .findFirst()
            .ifPresentOrElse(
                existing -> {
                    int idx = editingItems.indexOf(existing);
                    editingItems.set(idx, new SaleItem(
                        existing.id(), existing.saleId(), existing.variantId(), existing.variantName(),
                        existing.sku(), existing.productName(), existing.quantity() + 1, existing.pricePerUnit(),
                        existing.costPerUnit(), existing.taxRate(), existing.taxAmount(),
                        existing.appliedTaxRate(), existing.appliedTaxAmount(), 
                        existing.taxRuleSnapshot(), existing.discountAmount(), null
                    ));
                },
                () -> {
                    editingItems.add(new SaleItem(
                        null, currentSale.id(), variant.id(), variant.name(),
                        variant.sku(), variant.productName() != null ? variant.productName() : "New Product", 
                        1, variant.price(), variant.costPrice(), 
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 
                        null, BigDecimal.ZERO, null
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
            
            invoiceLabel.setText("#" + currentSale.shortInvoiceNumber());
            statusBadge.setText(currentSale.status().replace("_", " ").toUpperCase());
            applyStatusStyle(currentSale.status());
            
            dateLabel.setText("Processed on " + TimeUtil.formatStandard(TimeUtil.toLocal(currentSale.saleDate())));
            customerNameLabel.setText(currentSale.customerName() != null ? currentSale.customerName() : "Walk-in Customer");
            customerContactLabel.setText(currentSale.customerPhone() != null ? currentSale.customerPhone() : "No contact info");
            
            billerLabel.setText("Biller: " + (currentSale.billerName() != null ? currentSale.billerName() : "System"));
            
            if (!saleDetails.transactions().isEmpty()) {
                Transaction tx = saleDetails.transactions().get(0);
                paymentMethodLabel.setText(tx.paymentMethodName() != null ? tx.paymentMethodName() : "External Payment");
            } else {
                paymentMethodLabel.setText("N/A");
            }

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
        
        BigDecimal totalTax = saleDetails.items().stream()
                .map(i -> i.appliedTaxAmount() != null ? i.appliedTaxAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal lineItemDiscount = saleDetails.items().stream()
                .map(i -> i.discountAmount() != null ? i.discountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal billDiscount = currentSale.discount() != null ? currentSale.discount() : BigDecimal.ZERO;
        BigDecimal totalDiscount = lineItemDiscount.add(billDiscount);
        
        BigDecimal grandTotal = currentSale.totalAmount() != null ? currentSale.totalAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount = currentSale.paidAmount() != null ? currentSale.paidAmount() : BigDecimal.ZERO;
        
        subtotalLabel.setText(CurrencyUtil.format(subtotal));
        taxTotalLabel.setText(CurrencyUtil.format(totalTax));
        discountTotalLabel.setText("-" + CurrencyUtil.format(totalDiscount));
        BigDecimal totalRefunded = saleDetails.transactions().stream()
                .filter(t -> "refund".equals(t.type()) && "completed".equals(t.status()))
                .map(t -> t.amount().abs())
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
            com.possum.shared.dto.GeneralSettings generalSettings = settingsStore.loadGeneralSettings();
            com.possum.shared.dto.BillSettings billSettings = settingsStore.loadBillSettings();
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
            // Populate selections
            customerCombo.setItems(FXCollections.observableArrayList(salesService.getAllCustomers()));
            paymentMethodCombo.setItems(FXCollections.observableArrayList(salesService.getPaymentMethods()));
            
            // Pre-select current customer
            long currentCustomerId = currentSale.customerId() != null ? currentSale.customerId() : -1L;
            customerCombo.getItems().stream()
                .filter(c -> c.id() == currentCustomerId)
                .findFirst()
                .ifPresent(customerCombo::setValue);
            
            // Pre-select current payment method
            long currentMethodId = saleDetails.transactions().stream()
                    .filter(t -> "payment".equals(t.type()))
                    .map(com.possum.domain.model.Transaction::paymentMethodId)
                    .findFirst()
                    .orElse(-1L);
                    
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

        // Toggle layout visibility
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
            com.possum.domain.model.Customer selectedCustomer = customerCombo.getValue();
            com.possum.domain.model.PaymentMethod selectedMethod = paymentMethodCombo.getValue();
            
            Long newCustomerId = selectedCustomer != null ? selectedCustomer.id() : null;
            long newMethodId = selectedMethod != null ? selectedMethod.id() : -1L;
            
            com.possum.application.auth.AuthUser currentUser = com.possum.application.auth.AuthContext.getCurrentUser();
            boolean changed = false;

            // 1. Correct Customer
            if (!java.util.Objects.equals(currentSale.customerId(), newCustomerId)) {
                salesService.changeSaleCustomer(currentSale.id(), newCustomerId, currentUser.id());
                changed = true;
            }

            // 2. Correct Payment Method
            long currentMethodId = saleDetails.transactions().stream()
                    .filter(t -> "payment".equals(t.type()))
                    .map(com.possum.domain.model.Transaction::paymentMethodId)
                    .findFirst()
                    .orElse(-1L);
            
            if (newMethodId != -1 && newMethodId != currentMethodId) {
                salesService.changeSalePaymentMethod(currentSale.id(), newMethodId, currentUser.id());
                changed = true;
            }

            // 3. Batch Update Items
            List<UpdateSaleItemRequest> itemRequests = editingItems.stream()
                    .map(item -> new UpdateSaleItemRequest(
                            item.variantId(), item.quantity(), item.pricePerUnit(), item.discountAmount()
                    )).toList();
            
            // Note: We always update items if we're saving in edit mode to capture qty/price changes
            salesService.updateSaleItems(currentSale.id(), itemRequests, currentUser.id());
            changed = true;

            if (changed) {
                NotificationService.success("Bill updated successfully");
                loadSaleDetails(); // Reload data from DB
            }
            
            toggleEditMode(); // Exit edit mode
            
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to save sale details update: {}", e.getMessage(), e);
            NotificationService.error("Update failed: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    @FXML
    private void handleReturn() {
        Map<String, Object> params = Map.of("invoiceNumber", currentSale.invoiceNumber());
        workspaceManager.openDialog("Process Return", "/fxml/returns/create-return-dialog.fxml", params);
        loadSaleDetails();
    }
}

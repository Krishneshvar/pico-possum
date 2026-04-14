package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.people.CustomerService;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.*;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.infrastructure.printing.BillRenderer;
import com.possum.infrastructure.printing.PrintOutcome;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;
import com.possum.ui.common.ErrorHandler;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.BillPreviewDialog;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;

/**
 * Handles the "Complete Sale" flow: customer resolution, sale creation,
 * receipt printing, and UI feedback. Extracted from PosController to keep
 * the controller below 400 lines.
 */
public class SaleCompletionHandler {

    public interface Callbacks {
        void onSaleSuccess();
    }

    private final SalesService salesService;
    private final CustomerService customerService;
    private final PrinterService printerService;
    private final SettingsStore settingsStore;
    private final StackPane rootPane;
    private final NumberFormat currencyFormat;
    private final Callbacks callbacks;

    public SaleCompletionHandler(SalesService salesService,
                                  CustomerService customerService,
                                  PrinterService printerService,
                                  SettingsStore settingsStore,
                                  StackPane rootPane,
                                  NumberFormat currencyFormat,
                                  Callbacks callbacks) {
        this.salesService = salesService;
        this.customerService = customerService;
        this.printerService = printerService;
        this.settingsStore = settingsStore;
        this.rootPane = rootPane;
        this.currencyFormat = currencyFormat;
        this.callbacks = callbacks;
    }

    public void execute(SaleDraft bill, Button completeButton) {
        if (bill.getItems().isEmpty()) return;
        if (bill.getSelectedPaymentMethod() == null) {
            NotificationService.error("Please select a payment method");
            return;
        }

        final List<CreateSaleItemRequest> items = bill.getItems().stream()
                .map(it -> new CreateSaleItemRequest(it.getVariant().id(), it.getQuantity(), it.getDiscountAmount(), it.getPricePerUnit()))
                .toList();
        final BigDecimal discount = bill.isDiscountFixed()
                ? bill.getOverallDiscountValue()
                : bill.getSubtotal().multiply(bill.getOverallDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        final BigDecimal paidAmount = bill.isFullPayment() ? bill.getTotal() : bill.getAmountTendered();
        final Long paymentMethodId = bill.getSelectedPaymentMethod().id();
        final long userId = AuthContext.getCurrentUser().id();

        final String customerName = bill.getCustomerName().trim();
        final String customerPhone = bill.getCustomerPhone().trim();
        final String customerEmail = bill.getCustomerEmail().trim();
        final String customerAddress = bill.getCustomerAddress().trim();
        final Long existingCustomerId = bill.getSelectedCustomer() != null ? bill.getSelectedCustomer().id() : null;
        final BigDecimal totalToDisplay = bill.getTotal();
        final AuthUser taskUser = AuthContext.getCurrentUser();

        if (taskUser == null) {
            NotificationService.error("You are not logged in. Please log in again.");
            return;
        }

        completeButton.setDisable(true);
        completeButton.setText("Processing...");

        javafx.concurrent.Task<SaleResponse> task = new javafx.concurrent.Task<>() {
            @Override
            protected SaleResponse call() throws Exception {
                AuthContext.setCurrentUser(taskUser);
                try {
                    Long cId = existingCustomerId;
                    if (cId == null && (!customerName.isEmpty() || !customerPhone.isEmpty())) {
                        try {
                            Optional<Customer> existing = customerService.getCustomers(
                                    new com.possum.shared.dto.CustomerFilter(customerPhone, 1, 1, 0, 10, "name", "asc")
                            ).items().stream().filter(c -> c.phone().equals(customerPhone)).findFirst();

                            if (existing.isPresent()) {
                                cId = existing.get().id();
                            } else {
                                Customer created = customerService.createCustomer(customerName, customerPhone, customerEmail, customerAddress);
                                cId = created.id();
                                final String name = created.name();
                                Platform.runLater(() -> NotificationService.success("New customer added: " + name));
                            }
                        } catch (Exception e) {
                            Platform.runLater(() -> NotificationService.warning("Failed to automatically add customer: " + e.getMessage()));
                        }
                    }

                    CreateSaleRequest request = new CreateSaleRequest(
                            items, cId,
                            discount.compareTo(BigDecimal.ZERO) > 0 ? discount : null,
                            List.of(new PaymentRequest(paidAmount, paymentMethodId))
                    );
                    return salesService.createSale(request, userId);
                } finally {
                    AuthContext.clear();
                }
            }
        };

        task.setOnSucceeded(e -> {
            SaleResponse resp = task.getValue();
            completeButton.setText("Complete Sale");
            if (confirmPrint()) printReceipt(resp);
            NotificationService.success("Sale completed! Total: " + currencyFormat.format(totalToDisplay));
            callbacks.onSaleSuccess();
            completeButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            LoggingConfig.getLogger().error("Sale completion failed", ex);
            NotificationService.error("Sale failed: " + ErrorHandler.toUserMessage(ex));
            completeButton.setText("Complete Sale");
            completeButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private boolean confirmPrint() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Print Receipt"); a.setHeaderText(null);
        a.setContentText("Do you want to print the bill?");
        a.initOwner(rootPane.getScene().getWindow());
        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType no  = new ButtonType("No",  ButtonBar.ButtonData.NO);
        a.getButtonTypes().setAll(yes, no);
        return a.showAndWait().filter(r -> r == yes).isPresent();
    }

    private void printReceipt(SaleResponse sale) {
        try {
            GeneralSettings general = settingsStore.loadGeneralSettings();
            BillSettings bill = settingsStore.loadBillSettings();
            String html = BillRenderer.renderBill(sale, general, bill);
            printerService.printInvoiceDetailed(html, general.getDefaultPrinterName(), bill.getPaperWidth())
                    .thenAccept(this::handlePrintOutcome)
                    .exceptionally(ex -> { Platform.runLater(() -> NotificationService.error("Print error: " + ex.getMessage())); return null; });
            Platform.runLater(() -> new BillPreviewDialog(html, rootPane.getScene().getWindow()).showAndWait());
        } catch (Exception ex) {
            NotificationService.error("Failed to prepare receipt: " + ex.getMessage());
        }
    }

    private void handlePrintOutcome(PrintOutcome outcome) {
        if (!outcome.success())
            Platform.runLater(() -> NotificationService.warning("Print failed: " + outcome.message()));
    }
}

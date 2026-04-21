package com.picopossum.ui.sales;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.sales.SalesService;
import com.picopossum.application.sales.dto.SaleResponse;
import com.picopossum.domain.model.Sale;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.infrastructure.printing.BillRenderer;
import com.picopossum.infrastructure.printing.PrintOutcome;
import com.picopossum.infrastructure.printing.PrinterService;
import com.picopossum.ui.common.ErrorHandler;
import com.picopossum.ui.common.controls.NotificationService;
import com.picopossum.ui.common.dialogs.DialogStyler;
import com.picopossum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SalesHistoryActionsHandler {

    private final SalesService salesService;
    private final SettingsStore settingsStore;
    private final PrinterService printerService;
    private final WorkspaceManager workspaceManager;
    private final Runnable onDataChanged;

    public SalesHistoryActionsHandler(SalesService salesService,
                                      SettingsStore settingsStore,
                                      PrinterService printerService,
                                      WorkspaceManager workspaceManager,
                                      Runnable onDataChanged) {
        this.salesService = salesService;
        this.settingsStore = settingsStore;
        this.printerService = printerService;
        this.workspaceManager = workspaceManager;
        this.onDataChanged = onDataChanged;
    }

    public List<MenuItem> buildActionsMenu(Sale sale) {
        List<MenuItem> items = new ArrayList<>();

        if (isLegacySale(sale)) {
            MenuItem viewLegacy = new MenuItem("📄 View Legacy Summary");
            viewLegacy.setOnAction(e -> LegacySaleSummaryDialog.show(sale));
            items.add(viewLegacy);
            return items;
        }

        MenuItem viewItem = new MenuItem("👁 View Details");
        viewItem.setOnAction(e -> handleView(sale));
        items.add(viewItem);

        MenuItem printItem = new MenuItem("🖨 Print Invoice");
        printItem.setOnAction(e -> handlePrint(sale));
        items.add(printItem);

        if (!"cancelled".equals(sale.status()) && !"refunded".equals(sale.status())) {
            MenuItem returnItem = new MenuItem("↩ Return Items");
            returnItem.setOnAction(e -> handleReturn(sale));
            items.add(returnItem);

            MenuItem editItem = new MenuItem("✏️ Edit Bill");
            editItem.setOnAction(e -> handleEdit(sale));

            MenuItem cancelItem = new MenuItem("❌ Cancel Sale");
            cancelItem.getStyleClass().add("logout-menu-item");
            cancelItem.setOnAction(e -> handleCancel(sale));
            
            items.add(new SeparatorMenuItem());
            items.add(editItem);
            items.add(cancelItem);
        }

        return items;
    }

    public void handleView(Sale sale) {
        if (sale == null) return;
        if (isLegacySale(sale)) {
            LegacySaleSummaryDialog.show(sale);
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("sale", sale);
        workspaceManager.openOrFocusWindow("Bill: " + sale.invoiceNumber(), "/fxml/sales/sale-detail-view.fxml", params);
    }

    public void handlePrint(Sale sale) {
        if (sale == null) return;
        if (isLegacySale(sale)) {
            NotificationService.warning("Legacy bills cannot be reprinted because line-item details are unavailable.");
            return;
        }
        try {
            SaleResponse saleResponse = salesService.getSaleDetails(sale.id());
            com.picopossum.shared.dto.GeneralSettings generalSettings = settingsStore.loadGeneralSettings();
            com.picopossum.shared.dto.BillSettings billSettings = settingsStore.loadBillSettings();
            String billHtml = BillRenderer.renderBill(saleResponse, generalSettings, billSettings);

            printerService.printInvoiceDetailed(
                            billHtml,
                            generalSettings.getDefaultPrinterName(),
                            billSettings.getPaperWidth()
                    )
                    .thenAccept(this::notifyPrintResult)
                    .exceptionally(ex -> {
                        Platform.runLater(() -> NotificationService.error("Print error: " + ex.getMessage()));
                        return null;
                    });
        } catch (Exception ex) {
            NotificationService.error("Unable to print this bill: " + ex.getMessage());
        }
    }

    private void notifyPrintResult(PrintOutcome outcome) {
        Platform.runLater(() -> {
            if (outcome.success()) {
                String printer = outcome.printerName() != null ? outcome.printerName() : "configured printer";
                NotificationService.success("Invoice sent to " + printer);
            } else {
                NotificationService.warning("Print failed: " + outcome.message());
            }
        });
    }

    public void handleEdit(Sale sale) {
        if (sale == null) return;
        if (isLegacySale(sale)) {
            LegacySaleSummaryDialog.show(sale);
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("sale", sale);
        params.put("editing", true);
        workspaceManager.openOrFocusWindow("Bill: " + sale.invoiceNumber(), "/fxml/sales/sale-detail-view.fxml", params);
    }

    public void handleReturn(Sale sale) {
        if (sale == null) return;
        if (isLegacySale(sale)) {
            NotificationService.warning("Legacy bills do not have item-level rows for returns.");
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("invoiceNumber", sale.invoiceNumber());
        workspaceManager.openDialog("Process Return", "/fxml/returns/create-return-dialog.fxml", params);
        onDataChanged.run();
    }

    public void handleCancel(Sale sale) {
        if (sale == null) return;
        if (isLegacySale(sale)) {
            NotificationService.warning("Legacy bills are read-only and cannot be cancelled.");
            return;
        }
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(alert);
        alert.setTitle("Cancel Sale");
        alert.setHeaderText("Cancel Invoice #" + sale.invoiceNumber());
        alert.setContentText("Are you sure you want to cancel this sale? This will restore inventory stock.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            CompletableFuture.runAsync(() -> {
                salesService.cancelSale(sale.id());
            })
            .thenRun(() -> Platform.runLater(onDataChanged))
            .exceptionally(ex -> {
                LoggingConfig.getLogger().error("Failed to cancel sale: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    DialogStyler.apply(error);
                    error.setContentText("Failed to cancel sale: " + ErrorHandler.toUserMessage(ex));
                    error.show();
                });
                return null;
            });
        }
    }

    private boolean isLegacySale(Sale sale) {
        if (sale == null) return false;
        return "legacy".equalsIgnoreCase(sale.status()) || (sale.id() != null && sale.id() < 0);
    }
}

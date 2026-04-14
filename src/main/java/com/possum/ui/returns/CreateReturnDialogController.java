package com.possum.ui.returns;

import com.possum.application.auth.AuthContext;
import com.possum.application.returns.ReturnsService;
import com.possum.application.returns.dto.CreateReturnItemRequest;
import com.possum.application.returns.dto.CreateReturnRequest;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.repositories.SalesRepository;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.navigation.Parameterizable;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import com.possum.shared.util.CurrencyUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CreateReturnDialogController implements Parameterizable {

    @FXML private TextField saleInput;
    @FXML private Button findSaleButton;
    @FXML private VBox saleDetailsArea;
    @FXML private Label saleIdLabel;
    @FXML private Label invoiceLabel;
    @FXML private Label customerLabel;
    
    @FXML private HBox refundSummaryCard;
    @FXML private Label totalRefundLabel;
    @FXML private Label itemsSelectedLabel;
    
    @FXML private VBox itemsListContainer;
    @FXML private TextArea reasonArea;
    @FXML private Button submitButton;
    @FXML private Button cancelButton;

    private final SalesService salesService;
    private final SalesRepository salesRepository;
    private final ReturnsService returnsService;

    private Sale currentSale;
    private SaleResponse saleDetails;
    private final List<ReturnItemRow> itemRows = new ArrayList<>();
    
    public interface OnSuccessCallback {
        void onSuccess();
    }
    
    private OnSuccessCallback onSuccess;

    public CreateReturnDialogController(SalesService salesService, SalesRepository salesRepository, ReturnsService returnsService) {
        this.salesService = salesService;
        this.salesRepository = salesRepository;
        this.returnsService = returnsService;
    }

    @FXML
    public void initialize() {
        saleDetailsArea.setVisible(false);
        submitButton.setDisable(true);
        totalRefundLabel.setText(CurrencyUtil.format(BigDecimal.ZERO));
        itemsSelectedLabel.setText("0 items selected");

        saleInput.setOnAction(e -> handleFindSale());
        findSaleButton.setOnAction(e -> handleFindSale());
        
        FontIcon searchIcon = new FontIcon("bx-search-alt");
        searchIcon.setIconSize(16);
        searchIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        findSaleButton.setGraphic(searchIcon);

        FontIcon returnIcon = new FontIcon("bx-undo");
        returnIcon.setIconSize(18);
        returnIcon.setIconColor(javafx.scene.paint.Color.valueOf("#ef4444")); // Matches -color-error
        submitButton.setGraphic(returnIcon);

        FontIcon closeIcon = new FontIcon("bx-x");
        closeIcon.setIconSize(18);
        closeIcon.setIconColor(javafx.scene.paint.Color.valueOf("#1e293b")); // Matches -color-text-main
        cancelButton.setGraphic(closeIcon);
        
        cancelButton.setOnAction(e -> ((Stage)cancelButton.getScene().getWindow()).close());
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params != null && params.containsKey("invoiceNumber")) {
            String inv = (String) params.get("invoiceNumber");
            saleInput.setText(inv);
            Platform.runLater(this::handleFindSale);
        }
    }

    public void setOnSuccess(OnSuccessCallback callback) {
        this.onSuccess = callback;
    }

    private void handleFindSale() {
        String input = saleInput.getText().trim();
        if (input.isEmpty()) return;

        try {
            // First try by Invoice Number
            Optional<Sale> sale = salesRepository.findSaleByInvoiceNumber(input);
            
            // Fallback to ID if it's a number and not found by invoice
            if (sale.isEmpty() && input.matches("\\d+")) {
                sale = salesRepository.findSaleById(Long.parseLong(input));
            }

            sale.ifPresentOrElse(this::loadSaleDetails, () -> NotificationService.error("Sale not found"));
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Error finding sale", e);
            NotificationService.error("Error finding sale: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    private void loadSaleDetails(Sale sale) {
        this.currentSale = sale;
        this.saleDetails = salesService.getSaleDetails(sale.id());
        
        invoiceLabel.setText("#" + sale.shortInvoiceNumber());
        saleIdLabel.setText("ID: " + sale.id());
        customerLabel.setText(sale.customerName() != null ? sale.customerName() : "Walk-in Customer");
        
        saleDetailsArea.setVisible(true);
        renderItemsList();
    }

    private void renderItemsList() {
        itemsListContainer.getChildren().clear();
        itemRows.clear();

        for (SaleItem item : saleDetails.items()) {
            int available = item.quantity() - (item.returnedQuantity() != null ? item.returnedQuantity() : 0);
            if (available <= 0) continue;

            ReturnItemRow row = new ReturnItemRow(item, available);
            itemRows.add(row);
            itemsListContainer.getChildren().add(row.node);
        }
    }

    private void updateSummary() {
        BigDecimal totalRefund = BigDecimal.ZERO;
        int selectedCount = 0;

        for (ReturnItemRow row : itemRows) {
            if (row.isSelected()) {
                selectedCount++;
                totalRefund = totalRefund.add(row.getRefundAmount());
            }
        }

        totalRefundLabel.setText(CurrencyUtil.format(totalRefund));
        itemsSelectedLabel.setText(selectedCount + " items selected");
        submitButton.setDisable(selectedCount == 0);
    }

    @FXML
    private void handleSubmit() {
        String reason = reasonArea.getText().trim();
        if (reason.isEmpty()) {
            NotificationService.error("Reason is required");
            return;
        }

        List<CreateReturnItemRequest> items = new ArrayList<>();
        for (ReturnItemRow row : itemRows) {
            if (row.isSelected()) {
                items.add(new CreateReturnItemRequest(row.item.id(), row.getQuantity()));
            }
        }

        try {
            CreateReturnRequest request = new CreateReturnRequest(
                currentSale.id(),
                items,
                reason,
                AuthContext.getCurrentUser().id()
            );

            returnsService.createReturn(request);
            NotificationService.success("Return processed successfully");
            if (onSuccess != null) onSuccess.onSuccess();
            ((Stage)submitButton.getScene().getWindow()).close();
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Return failed", e);
            NotificationService.error("Return failed: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    private class ReturnItemRow {
        final SaleItem item;
        final CheckBox checkBox;
        final TextField qtyField;
        final Label lineTotalLabel;
        final VBox node;
        private int currentQty;

        ReturnItemRow(SaleItem item, int maxQty) {
            this.item = item;
            this.currentQty = maxQty;

            checkBox = new CheckBox();
            checkBox.getStyleClass().add("custom-checkbox");

            Label nameLabel = new Label(item.productName());
            nameLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #0f172a;");
            
            Label variantLabel = new Label(item.variantName() != null ? item.variantName() : "Standard Variant");
            variantLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
            
            Label detailsLabel = new Label(String.format("Unit Price: %s  |  Available: %d", 
                CurrencyUtil.format(item.pricePerUnit()), maxQty));
            detailsLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

            VBox nameArea = new VBox(2, nameLabel, variantLabel, detailsLabel);
            HBox itemInfo = new HBox(15, checkBox, nameArea);
            itemInfo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            // Custom Quantity Controls
            qtyField = new TextField(String.valueOf(currentQty));
            qtyField.setPrefWidth(45);
            qtyField.setAlignment(javafx.geometry.Pos.CENTER);
            qtyField.setEditable(false);
            qtyField.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-padding: 4; -fx-font-weight: bold;");

            Button minusBtn = new Button("-");
            minusBtn.setPrefSize(30, 30);
            minusBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-weight: 800; -fx-cursor: hand; -fx-background-radius: 4;");
            
            Button plusBtn = new Button("+");
            plusBtn.setPrefSize(30, 30);
            plusBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-weight: 800; -fx-cursor: hand; -fx-background-radius: 4;");

            minusBtn.setOnAction(e -> {
                if (currentQty > 1) {
                    currentQty--;
                    qtyField.setText(String.valueOf(currentQty));
                    updateLineTotal();
                    updateSummary();
                }
            });

            plusBtn.setOnAction(e -> {
                if (currentQty < maxQty) {
                    currentQty++;
                    qtyField.setText(String.valueOf(currentQty));
                    updateLineTotal();
                    updateSummary();
                }
            });

            HBox qtyControls = new HBox(5, minusBtn, qtyField, plusBtn);
            qtyControls.setAlignment(javafx.geometry.Pos.CENTER);
            qtyControls.setDisable(true);
            qtyControls.setOpacity(0.5);

            lineTotalLabel = new Label("");
            lineTotalLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #ef4444; -fx-font-size: 16px; -fx-min-width: 100;");
            lineTotalLabel.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            VBox actionArea = new VBox(4, new Label("Qty to Return"), qtyControls);
            ((Label)actionArea.getChildren().get(0)).setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-font-weight: 800; -fx-letter-spacing: 0.5;");
            actionArea.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            
            HBox mainRow = new HBox(20, itemInfo, new Region(), actionArea, lineTotalLabel);
            mainRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox.setHgrow(mainRow.getChildren().get(1), Priority.ALWAYS);
            
            mainRow.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16;");

            checkBox.selectedProperty().addListener((obs, old, selected) -> {
                qtyControls.setDisable(!selected);
                qtyControls.setOpacity(selected ? 1.0 : 0.5);
                mainRow.setStyle(selected 
                    ? "-fx-background-color: #fff1f2; -fx-border-color: #fecaca; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16;" 
                    : "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16;");
                updateLineTotal();
                updateSummary();
            });

            this.node = new VBox(mainRow);
            updateLineTotal();
        }

        void updateLineTotal() {
            BigDecimal amount = item.pricePerUnit().multiply(BigDecimal.valueOf(currentQty));
            lineTotalLabel.setText(isSelected() ? CurrencyUtil.format(amount) : "");
        }

        boolean isSelected() {
            return checkBox.isSelected();
        }

        int getQuantity() {
            return currentQty;
        }

        BigDecimal getRefundAmount() {
            return item.pricePerUnit().multiply(BigDecimal.valueOf(currentQty));
        }
    }
}

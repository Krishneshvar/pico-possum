package com.possum.ui.purchase;

import com.possum.application.auth.AuthContext;
import com.possum.application.purchase.PurchaseService;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.shared.util.TimeUtil;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.DialogStyler;
import com.possum.ui.navigation.Parameterizable;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.Map;

public class PurchaseOrderDetailController implements Parameterizable {

    @FXML private Label poNumberLabel;
    @FXML private Label statusLabel;
    @FXML private Label orderDateLabel;
    @FXML private Label supplierNameLabel;
    @FXML private Label createdByLabel;
    @FXML private Label receivedDateLabel;
    @FXML private DataTableView<PurchaseOrderItem> itemsTable;
    @FXML private Label totalItemsLabel;
    @FXML private Label totalQuantityLabel;
    @FXML private Label totalCostLabel;
    @FXML private Button receiveButton;
    @FXML private Button editButton;
    @FXML private Button cancelButton;

    private PurchaseService purchaseService;
    private WorkspaceManager workspaceManager;
    private PurchaseService.PurchaseOrderDetail orderDetail;
    private Runnable onActionCallback;

    public PurchaseOrderDetailController(PurchaseService purchaseService, WorkspaceManager workspaceManager) {
        this.purchaseService = purchaseService;
        this.workspaceManager = workspaceManager;
    }

    @Override
    public void setParameters(Map<String, Object> params) {
        if (params != null && params.containsKey("orderId")) {
            long orderId = ((Number) params.get("orderId")).longValue();
            if (params.containsKey("onAction")) {
                onActionCallback = (Runnable) params.get("onAction");
            }
            Platform.runLater(() -> loadOrderDetails(orderId));
        }
    }

    @FXML
    public void initialize() {
        setupItemsTable();
    }

    private void setupItemsTable() {
        TableColumn<PurchaseOrderItem, String> productCol = new TableColumn<>("Product | Variant");
        productCol.setPrefWidth(300);
        productCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    PurchaseOrderItem poi = getTableRow().getItem();
                    VBox vbox = new VBox(2);
                    Label productLabel = new Label(poi.productName());
                    productLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
                    Label variantLabel = new Label(poi.sku() + " (" + poi.variantName() + ")");
                    variantLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");
                    vbox.getChildren().addAll(productLabel, variantLabel);
                    setGraphic(vbox);
                }
            }
        });

        TableColumn<PurchaseOrderItem, Integer> qtyCol = new TableColumn<>("Quantity");
        qtyCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().quantity()));
        qtyCol.setPrefWidth(100);
        qtyCol.setStyle("-fx-alignment: CENTER;");
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<PurchaseOrderItem, BigDecimal> costCol = new TableColumn<>("Unit Cost");
        costCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().unitCost()));
        costCol.setPrefWidth(120);
        costCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        costCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(item));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: gray;");
                }
            }
        });

        TableColumn<PurchaseOrderItem, BigDecimal> totalCol = new TableColumn<>("Total");
        totalCol.setPrefWidth(120);
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        totalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    PurchaseOrderItem poi = getTableRow().getItem();
                    BigDecimal total = poi.unitCost().multiply(BigDecimal.valueOf(poi.quantity()));
                    setText(CurrencyUtil.format(total));
                    setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1976d2;");
                }
            }
        });

        itemsTable.getTableView().getColumns().setAll(productCol, qtyCol, costCol, totalCol);
        itemsTable.setEmptyMessage("No items in this purchase order");
        itemsTable.setEmptySubtitle("This order contains no product records.");
    }

    private void loadOrderDetails(long orderId) {
        try {
            orderDetail = purchaseService.getPurchaseOrderById(orderId);
            displayOrderDetails();
        } catch (Exception e) {
            NotificationService.error("Failed to load purchase order details");
            workspaceManager.closeActiveWindow();
        }
    }

    private void displayOrderDetails() {
        if (orderDetail == null) return;

        poNumberLabel.setText(orderDetail.purchaseOrder().shortInvoiceNumber());
        orderDateLabel.setText(TimeUtil.formatStandard(TimeUtil.toLocal(orderDetail.purchaseOrder().orderDate())));
        supplierNameLabel.setText(orderDetail.purchaseOrder().supplierName());
        createdByLabel.setText(orderDetail.purchaseOrder().createdByName());

        // Status with indicator
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        Circle indicator = new Circle(5);
        Label statusText = new Label(orderDetail.purchaseOrder().status().toUpperCase());
        statusText.setFont(Font.font("System", FontWeight.BOLD, 12));

        switch (orderDetail.purchaseOrder().status().toLowerCase()) {
            case "pending" -> {
                indicator.setFill(Color.ORANGE);
                statusText.setTextFill(Color.ORANGE);
            }
            case "received" -> {
                indicator.setFill(Color.GREEN);
                statusText.setTextFill(Color.GREEN);
            }
            case "cancelled" -> {
                indicator.setFill(Color.RED);
                statusText.setTextFill(Color.RED);
            }
        }

        statusBox.getChildren().addAll(indicator, statusText);
        statusLabel.setGraphic(statusBox);

        if (orderDetail.purchaseOrder().receivedDate() != null) {
            receivedDateLabel.setText(TimeUtil.formatStandard(TimeUtil.toLocal(orderDetail.purchaseOrder().receivedDate())));
            receivedDateLabel.setVisible(true);
        } else {
            receivedDateLabel.setVisible(false);
        }

        itemsTable.setItems(FXCollections.observableArrayList(orderDetail.items()));

        int totalQty = orderDetail.items().stream().mapToInt(PurchaseOrderItem::quantity).sum();
        BigDecimal totalCost = orderDetail.items().stream()
                .map(item -> item.unitCost().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalItemsLabel.setText(String.valueOf(orderDetail.items().size()));
        totalQuantityLabel.setText(String.valueOf(totalQty));
        totalCostLabel.setText(CurrencyUtil.format(totalCost));

        boolean isPending = "pending".equals(orderDetail.purchaseOrder().status());
        receiveButton.setVisible(isPending);
        receiveButton.setManaged(isPending);
        editButton.setVisible(isPending);
        editButton.setManaged(isPending);
        cancelButton.setVisible(isPending);
        cancelButton.setManaged(isPending);
    }

    @FXML
    private void handleReceive() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(confirm);
        confirm.setTitle("Receive Purchase Order");
        confirm.setHeaderText("Receive " + orderDetail.purchaseOrder().shortInvoiceNumber() + "?");
        confirm.setContentText("This will create inventory lots and update stock levels. This action cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    long userId = AuthContext.getCurrentUser().id();
                    purchaseService.receivePurchaseOrder(orderDetail.purchaseOrder().id(), userId);
                    NotificationService.success("Purchase order received successfully");
                    if (onActionCallback != null) onActionCallback.run();
                    loadOrderDetails(orderDetail.purchaseOrder().id());
                } catch (Exception e) {
                    NotificationService.error("Failed to receive order: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleEdit() {
        Map<String, Object> params = Map.of(
                "order", orderDetail.purchaseOrder(),
                "onSave", (Runnable) () -> {
                    if (onActionCallback != null) onActionCallback.run();
                    loadOrderDetails(orderDetail.purchaseOrder().id());
                }
        );
        workspaceManager.openWindow("Edit Purchase Order", "/fxml/purchase/purchase-order-form-view.fxml", params);
    }

    @FXML
    private void handleCancel() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        DialogStyler.apply(confirm);
        confirm.setTitle("Cancel Purchase Order");
        confirm.setHeaderText("Cancel " + orderDetail.purchaseOrder().shortInvoiceNumber() + "?");
        confirm.setContentText("This action cannot be undone and the order will not be fulfilled.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    long userId = AuthContext.getCurrentUser().id();
                    purchaseService.cancelPurchaseOrder(orderDetail.purchaseOrder().id(), userId);
                    NotificationService.success("Purchase order cancelled");
                    if (onActionCallback != null) onActionCallback.run();
                    loadOrderDetails(orderDetail.purchaseOrder().id());
                } catch (Exception e) {
                    NotificationService.error("Failed to cancel order: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleClose() {
        workspaceManager.closeActiveWindow();
    }
}

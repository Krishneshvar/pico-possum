package com.possum.ui.returns;

import com.possum.application.returns.ReturnsService;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Return;
import com.possum.domain.model.Sale;
import com.possum.domain.model.PaymentMethod;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.components.ButtonFactory;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.workspace.WorkspaceManager;
import com.possum.shared.util.TimeUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class ReturnsController extends AbstractCrudController<Return, ReturnFilter> {
    
    @FXML private Button createReturnButton;
    
    private final ReturnsService returnsService;
    private final SalesService salesService;
    
    private LocalDate fromDate = null;
    private LocalDate toDate = null;
    private BigDecimal currentMinAmount = null;
    private BigDecimal currentMaxAmount = null;
    private List<Long> currentPaymentMethodIds = null;

    public ReturnsController(ReturnsService returnsService, 
                            SalesService salesService, 
                            WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.returnsService = returnsService;
        this.salesService = salesService;
    }

    @Override
    protected void setupPermissions() {
        if (createReturnButton != null) {
            com.possum.ui.common.UIPermissionUtil.requirePermission(
                createReturnButton, 
                com.possum.application.auth.Permissions.RETURNS_MANAGE
            );
            FontIcon returnIcon = new FontIcon("bx-undo");
            returnIcon.setIconSize(16);
            returnIcon.setIconColor(javafx.scene.paint.Color.valueOf("#ef4444"));
            createReturnButton.setGraphic(returnIcon);
        }
    }

    @Override
    protected void setupTable() {
        dataTable.setEmptyMessage("No returns found");
        dataTable.setEmptySubtitle("Returns will appear here when processed.");
        
        TableColumn<Return, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().invoiceNumber()));
        invoiceCol.setSortable(false);
        invoiceCol.setCellFactory(col -> new TableCell<Return, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(item);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(10);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    
                    Label label = new Label(item);
                    Button viewBtn = ButtonFactory.createIconButton("bx-show-alt", "View Sale Details", () -> {});
                    viewBtn.getStyleClass().add("btn-edit-stock");
                    
                    Return returnRec = getTableView().getItems().get(getIndex());
                    viewBtn.setOnAction(e -> handleViewDetails(returnRec));
                    
                    container.getChildren().addAll(label, viewBtn);
                    setGraphic(container);
                    setText(null);
                }
            }
        });
        
        TableColumn<Return, String> paymentCol = new TableColumn<>("Payment Method");
        paymentCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().paymentMethodName()));

        TableColumn<Return, BigDecimal> refundCol = new TableColumn<>("Refund");
        refundCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().totalRefund()));
        refundCol.setCellFactory(col -> new TableCell<Return, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyUtil.format(item));
            }
        });
        
        TableColumn<Return, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().reason()));

        TableColumn<Return, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().createdAt()));
        dateCol.setCellFactory(col -> new TableCell<Return, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    LocalDateTime localZoned = TimeUtil.toLocal(item);
                    setText(localZoned != null ? TimeUtil.formatStandard(localZoned) : null);
                }
            }
        });
        
        dataTable.getTableView().getColumns().addAll(invoiceCol, paymentCol, refundCol, reasonCol, dateCol);
    }

    @Override
    protected void setupFilters() {
        List<PaymentMethod> pms = salesService.getPaymentMethods();
        filterBar.addMultiSelectFilter("paymentMethod", "All Payments", pms, 
                PaymentMethod::name, false);

        filterBar.addDateFilter("fromDate", "From Date");
        filterBar.addDateFilter("toDate", "To Date");
        filterBar.addTextFilter("minAmount", "Min Refund");
        filterBar.addTextFilter("maxAmount", "Max Refund");
    }

    @Override
    protected ReturnFilter buildFilter() {
        String searchTerm = filterBar.getSearchTerm();
        fromDate = (LocalDate) filterBar.getFilterValue("fromDate");
        toDate = (LocalDate) filterBar.getFilterValue("toDate");
        currentMinAmount = parseBigDecimal(filterBar.getFilterValue("minAmount"));
        currentMaxAmount = parseBigDecimal(filterBar.getFilterValue("maxAmount"));
        
        @SuppressWarnings("unchecked")
        List<PaymentMethod> selectedPms = (List<PaymentMethod>) filterBar.getFilterValue("paymentMethod");
        if (selectedPms == null || selectedPms.isEmpty()) {
            currentPaymentMethodIds = null;
        } else {
            currentPaymentMethodIds = selectedPms.stream()
                    .map(PaymentMethod::id)
                    .toList();
        }
        
        return new ReturnFilter(
            null,
            null,
            fromDate != null ? fromDate.atStartOfDay().toString() : null,
            toDate != null ? toDate.atTime(23, 59, 59).toString() : null,
            currentMinAmount,
            currentMaxAmount,
            currentPaymentMethodIds,
            searchTerm == null || searchTerm.isEmpty() ? null : searchTerm,
            getCurrentPage(),
            getPageSize(),
            "created_at",
            "DESC"
        );
    }

    @Override
    protected PagedResult<Return> fetchData(ReturnFilter filter) {
        return returnsService.getReturns(filter);
    }

    @Override
    protected String getEntityName() {
        return "returns";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Return";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Return entity) {
        return List.of(); // Returns use inline view button
    }

    @Override
    protected void deleteEntity(Return entity) throws Exception {
        throw new UnsupportedOperationException("Returns cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(Return entity) {
        return "Invoice #" + entity.invoiceNumber();
    }

    @FXML
    private void handleCreateReturn() {
        workspaceManager.openDialog("Process Return", "/fxml/returns/create-return-dialog.fxml");
        loadData();
    }

    private void handleViewDetails(Return returnRecord) {
        SaleResponse saleResponse = salesService.getSaleDetails(returnRecord.saleId());
        Sale sale = saleResponse.sale();
        if (sale == null) return;
        
        Map<String, Object> params = new HashMap<>();
        params.put("sale", sale);
        workspaceManager.openOrFocusWindow("Bill: " + sale.invoiceNumber(), "/fxml/sales/sale-detail-view.fxml", params);
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            String s = value.toString().replaceAll("[^0-9.\\-]", "");
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}

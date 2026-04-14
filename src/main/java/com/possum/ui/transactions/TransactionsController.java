package com.possum.ui.transactions;

import com.possum.shared.util.TimeUtil;
import com.possum.application.auth.AuthContext;
import com.possum.application.transactions.TransactionService;
import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.TransactionFilter;
import com.possum.ui.common.controllers.AbstractCrudController;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.workspace.WorkspaceManager;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;
import com.possum.shared.util.CurrencyUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionsController extends AbstractCrudController<Transaction, TransactionFilter> {
    
    private final TransactionService transactionService;
    private final com.possum.application.sales.SalesService salesService;
    
    private List<String> currentTypes = null;
    private List<Long> currentPaymentMethodIds = null;
    private LocalDate fromDate = null;
    private LocalDate toDate = null;
    private BigDecimal currentMinAmount = null;
    private BigDecimal currentMaxAmount = null;
    private String currentSortBy = "transaction_date";
    private String currentSortOrder = "DESC";

    public TransactionsController(TransactionService transactionService, 
                                  com.possum.application.sales.SalesService salesService,
                                  WorkspaceManager workspaceManager) {
        super(workspaceManager);
        this.transactionService = transactionService;
        this.salesService = salesService;
    }

    @Override
    protected void setupPermissions() {
        // No special permissions needed for viewing transactions
    }

    @Override
    protected void setupTable() {
        TableColumn<Transaction, String> refCol = new TableColumn<>("Reference Bill");
        refCol.setSortable(false);
        refCol.setCellValueFactory(cellData -> {
            Transaction tx = cellData.getValue();
            return new SimpleStringProperty(tx.shortInvoiceNumber() != null && !tx.shortInvoiceNumber().isEmpty() ? tx.shortInvoiceNumber() : "-");
        });
        refCol.setCellFactory(col -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Transaction tx = getTableRow() != null ? getTableRow().getItem() : null;
                boolean isLegacy = tx != null && "legacy".equalsIgnoreCase(tx.status());
                if (empty || item == null || "-".equals(item)) {
                    setText(item);
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(10);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    
                    Label label = new Label(item);
                    container.getChildren().add(label);

                    if (!isLegacy) {
                        javafx.scene.control.Button viewBtn = new javafx.scene.control.Button();
                        org.kordamp.ikonli.javafx.FontIcon viewIcon = new org.kordamp.ikonli.javafx.FontIcon("bx-show-alt");
                        viewIcon.setIconSize(16);
                        viewBtn.setGraphic(viewIcon);
                        viewBtn.getStyleClass().add("btn-edit-stock");
                        viewBtn.setTooltip(new javafx.scene.control.Tooltip("View Bill Details"));
                        viewBtn.setOnAction(e -> handleViewBill(item));
                        container.getChildren().add(viewBtn);
                    }

                    setGraphic(container);
                    setText(null);
                }
            }
        });

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setSortable(false);
        typeCol.setCellValueFactory(cellData -> {
            String type = cellData.getValue().type();
            if ("payment".equals(type)) return new SimpleStringProperty("Sale");
            return new SimpleStringProperty(com.possum.shared.util.TextFormatter.toTitleCase(type));
        });

        TableColumn<Transaction, String> paymentCol = new TableColumn<>("Payment Method");
        paymentCol.setSortable(false);
        paymentCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().paymentMethodName()));

        TableColumn<Transaction, BigDecimal> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().amount()));
        amountCol.setCellFactory(col -> new TableCell<Transaction, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CurrencyUtil.format(item.abs()));
                    setStyle(item.compareTo(BigDecimal.ZERO) < 0 ? "-fx-text-fill: #ef4444;" : "-fx-text-fill: #10b981;");
                }
            }
        });

        TableColumn<Transaction, LocalDateTime> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().transactionDate()));
        dateCol.setCellFactory(col -> new TableCell<Transaction, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    LocalDateTime localZoned = TimeUtil.toLocal(item);
                    setText(localZoned != null ? TimeUtil.formatStandard(localZoned) : "");
                }
            }
        });

        TableColumn<Transaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setSortable(false);
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().status()));
        statusCol.setCellFactory(col -> new TableCell<Transaction, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setGraphic(com.possum.ui.common.components.BadgeFactory.createTransactionStatusBadge(status));
                    setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(javafx.geometry.Pos.CENTER);
                    setText(null);
                }
            }
        });
        
        refCol.setId("invoice_number");
        typeCol.setId("type");
        typeCol.setSortable(true);
        paymentCol.setId("payment_method_name");
        amountCol.setId("amount");
        dateCol.setId("transaction_date");
        dataTable.getTableView().getColumns().addAll(List.of(refCol, typeCol, paymentCol, amountCol, dateCol));
        
        dataTable.getTableView().getSortOrder().addListener((javafx.beans.Observable obs) -> {
            if (!dataTable.getTableView().getSortOrder().isEmpty()) {
                TableColumn<Transaction, ?> col = dataTable.getTableView().getSortOrder().get(0);
                currentSortBy = col.getId() != null ? col.getId() : "transaction_date";
                currentSortOrder = col.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC";
                loadData();
            }
        });
    }

    @Override
    protected void setupFilters() {
        filterBar.addMultiSelectFilter("type", "All Types", 
            List.of("Sale", "Refund", "Purchase"),
            s -> s,
            false
        );

        List<com.possum.domain.model.PaymentMethod> pms = salesService.getPaymentMethods();
        filterBar.addMultiSelectFilter("paymentMethod", "All Payments", pms, 
            com.possum.domain.model.PaymentMethod::name,
            false);

        filterBar.addDateFilter("fromDate", "From Date");
        filterBar.addDateFilter("toDate", "To Date");
        filterBar.addTextFilter("minAmount", "Min Amount");
        filterBar.addTextFilter("maxAmount", "Max Amount");
        
        setupStandardFilterListener((filters, reload) -> {
            List<String> types = (List<String>) filters.get("type");
            if (types == null || types.isEmpty()) {
                currentTypes = null;
            } else {
                currentTypes = types.stream()
                    .map(t -> "Sale".equals(t) ? "payment" : t.toLowerCase())
                    .toList();
            }

            List<com.possum.domain.model.PaymentMethod> selectedPms = (List<com.possum.domain.model.PaymentMethod>) filters.get("paymentMethod");
            if (selectedPms == null || selectedPms.isEmpty()) {
                currentPaymentMethodIds = null;
            } else {
                currentPaymentMethodIds = selectedPms.stream()
                    .map(com.possum.domain.model.PaymentMethod::id)
                    .toList();
            }

            fromDate = (LocalDate) filters.get("fromDate");
            toDate = (LocalDate) filters.get("toDate");

            currentMinAmount = parseBigDecimal(filters.get("minAmount"));
            currentMaxAmount = parseBigDecimal(filters.get("maxAmount"));
            
            reload.run();
        });
    }

    @Override
    protected TransactionFilter buildFilter() {
        return new TransactionFilter(
            fromDate != null ? fromDate.atStartOfDay().toString() : null,
            toDate != null ? toDate.atTime(23, 59, 59).toString() : null,
            currentTypes,
            currentMinAmount,
            currentMaxAmount,
            currentPaymentMethodIds,
            null,
            getSearchOrNull(),
            getCurrentPage(),
            getPageSize(),
            currentSortBy,
            currentSortOrder
        );
    }

    @Override
    protected PagedResult<Transaction> fetchData(TransactionFilter filter) {
        Set<String> permissions = new HashSet<>(AuthContext.getCurrentUser().permissions());
        return transactionService.getTransactions(filter, permissions);
    }

    @Override
    protected String getEntityName() {
        return "transactions";
    }

    @Override
    protected String getEntityNameSingular() {
        return "Transaction";
    }

    @Override
    protected List<MenuItem> buildActionMenu(Transaction entity) {
        return List.of(); // No actions for transactions
    }

    @Override
    protected void deleteEntity(Transaction entity) throws Exception {
        throw new UnsupportedOperationException("Transactions cannot be deleted");
    }

    @Override
    protected String getEntityIdentifier(Transaction entity) {
        return entity.invoiceNumber() != null ? entity.invoiceNumber() : String.valueOf(entity.id());
    }

    private void handleViewBill(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isEmpty()) return;
        
        salesService.findSaleByInvoiceNumber(invoiceNumber).ifPresentOrElse(sale -> {
            java.util.Map<String, Object> params = new java.util.HashMap<>();
            params.put("sale", sale);
            workspaceManager.openOrFocusWindow("Bill: " + sale.invoiceNumber(), "/fxml/sales/sale-detail-view.fxml", params);
        }, () -> NotificationService.info("This reference is from legacy summary data and has no item-level bill details."));
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

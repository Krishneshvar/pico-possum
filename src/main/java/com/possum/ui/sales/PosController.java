package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.categories.CategoryService;
import com.possum.application.products.ProductService;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.TaxEngine;
import com.possum.domain.model.*;
import com.possum.domain.services.SaleCalculator;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.persistence.repositories.sqlite.SqlitePosDraftRepository;
import com.possum.ui.common.ErrorHandler;
import com.possum.ui.common.controls.DataTableView;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.sales.cells.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;

import com.possum.shared.util.CurrencyUtil;
import java.math.BigDecimal;
import java.util.*;

public class PosController implements CartCellHandler {

    @FXML private VBox leftVBox;
    @FXML private VBox cartCardVBox;
    @FXML private HBox searchDock;
    @FXML private VBox paymentCard;
    @FXML private HBox tenderedBalanceContainer;
    @FXML private StackPane rightPane;
    @FXML private DataTableView<CartItem> cartTable;

    private TableColumn<CartItem, String>   colSno, colSku, colProduct, colTotal, colMrp;
    private TableColumn<CartItem, CartItem> colQty, colPrice, colDiscountPct, colDiscountAmt;

    @FXML private TextField searchField;
    @FXML private Label totalQtyLabel, bottomTotalLabel, bottomMrpLabel, bottomPriceTotalLabel;

    @FXML private TextField quickProductName, quickVariantName, quickStock, quickPrice, quickCategorySearch;

    @FXML private ComboBox<Customer>       customerCombo;
    @FXML private TextField customerNameField, customerPhoneField, customerEmailField, customerAddressField;
    @FXML private ComboBox<PaymentMethod>  paymentMethodCombo;

    @FXML private ToggleButton btnFullPayment, btnPartialPayment;
    @FXML private TextField    discountField;
    @FXML private ToggleButton btnDiscountFixed, btnDiscountPercent;

    @FXML private Label subtotalLabel, totalDiscountLabel, taxLabel, totalLabel;
    @FXML private TextField tenderedField;
    @FXML private Label balanceTypeLabel, balanceLabel;

    @FXML private Button    completeButton;
    @FXML private HBox      billsFlowPane;
    @FXML private StackPane rootPane;

    private boolean  isAutofilling = false;
    private Long     selectedProductIdForQuickAdd  = null;
    private Category selectedCategoryForQuickAdd   = null;

    private final SalesService             salesService;
    private final com.possum.application.people.CustomerService customerService;
    private final ProductSearchIndex       searchIndex;
    private final TaxEngine                taxEngine;
    private final PrinterService           printerService;
    private final SettingsStore            settingsStore;
    private final ProductService           productService;
    private final CategoryService          categoryService;
    private final SaleCalculator           saleCalculator;
    private final SqlitePosDraftRepository posDraftRepository;

    private static final int MAX_BILLS = 9;
    private final List<SaleDraft> bills = new ArrayList<>();
    private SaleDraft currentBill;

    // Extracted helpers
    private PosAutocompleteManager autocomplete;
    private SaleCompletionHandler  completionHandler;

    public PosController(SalesService salesService,
                         com.possum.application.people.CustomerService customerService,
                         ProductSearchIndex searchIndex, TaxEngine taxEngine,
                         PrinterService printerService, SettingsStore settingsStore,
                         ProductService productService, CategoryService categoryService,
                         SaleCalculator saleCalculator,
                         SqlitePosDraftRepository posDraftRepository) {
        this.salesService     = salesService;
        this.customerService  = customerService;
        this.searchIndex      = searchIndex;
        this.taxEngine        = taxEngine;
        this.printerService   = printerService;
        this.settingsStore    = settingsStore;
        this.productService   = productService;
        this.categoryService  = categoryService;
        this.saleCalculator   = saleCalculator;
        this.posDraftRepository = posDraftRepository;
    }

    @FXML
    public void initialize() {
        isAutofilling = true;
        try {
            if (completeButton != null)
                com.possum.ui.common.UIPermissionUtil.requirePermission(completeButton,
                        com.possum.application.auth.Permissions.SALES_CREATE);

        for (int i = 0; i < MAX_BILLS; i++) { 
            int idx = i;
            SaleDraft d;
            try {
                d = posDraftRepository.loadBill(i)
                        .orElseGet(() -> { SaleDraft newD = new SaleDraft(); newD.setIndex(idx); return newD; });
            } catch (Exception ex) {
                com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to load bill draft at index " + i, ex);
                d = new SaleDraft();
                d.setIndex(idx);
            }
            bills.add(d); 
        }
        currentBill = bills.get(0);

        NotificationService.initialize(rootPane);
        setupTable();
        setupListeners();
        loadCombos();
        setupBillingToggles();
        renderBillsFlowPane();
        switchBill(0);
        taxEngine.init();

        autocomplete = new PosAutocompleteManager(new PosAutocompleteManager.Callbacks() {
            public void onProductSelected(Variant v)             { addToCart(v); searchField.clear(); }
            public void onProductSelectedForQuickAdd(Variant v)  { selectProductForQuickAdd(v); }
            public void onCategorySelectedForQuickAdd(Category c){ selectCategoryForQuickAdd(c); }
            public ProductSearchIndex getSearchIndex()           { return searchIndex; }
            public com.possum.application.categories.CategoryService getCategoryService() { return categoryService; }
        });
        autocomplete.setupSearchAutocomplete(searchField);
        autocomplete.setupQuickAddAutocomplete(quickProductName, quickVariantName);
        autocomplete.setupCategoryAutocomplete(quickCategorySearch);

        completionHandler = new SaleCompletionHandler(
                salesService, customerService, printerService, settingsStore,
                rootPane, java.text.NumberFormat.getCurrencyInstance(), () -> { handleClearCart(); loadCombos(); });

            setupKeyboardShortcuts();
            updatePaymentSectionState();
            setupLayoutSizing();
            
            // Prevent Quick Add from shrinking
            if (rightPane != null) {
                rightPane.setMinWidth(380);
            }
        } finally {
            isAutofilling = false;
        }
    }

    // ── Table Setup ───────────────────────────────────────────────────────────

    private void setupTable() {
        colSno        = new TableColumn<>("#");
        colSku        = new TableColumn<>("SKU");
        colProduct    = new TableColumn<>("Product");
        colQty        = new TableColumn<>("Qty");
        colPrice      = new TableColumn<>("Price");
        colMrp        = new TableColumn<>("MRP");
        colDiscountPct = new TableColumn<>("Disc %");
        colDiscountAmt = new TableColumn<>("Disc Amt");
        colTotal      = new TableColumn<>("Total");

        cartTable.getTableView().getColumns().clear();
        cartTable.getTableView().getColumns().addAll(List.of(
                colSno, colSku, colProduct, colQty, colPrice, colMrp, colDiscountPct, colDiscountAmt, colTotal));

        colSno.setPrefWidth(48); colSno.setResizable(false);
        colSno.setCellValueFactory(c -> {
            int idx = cartTable.getTableView().getItems().indexOf(c.getValue());
            return new SimpleStringProperty(idx < 0 ? "-" : String.valueOf(idx + 1));
        });

        colSku.setPrefWidth(95);
        colSku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVariant().sku()));

        colProduct.setPrefWidth(180);
        colProduct.setCellValueFactory(c -> {
            Variant v = c.getValue().getVariant();
            String d = v.productName();
            if (v.name() != null && !v.name().equalsIgnoreCase("Standard")) d += " (" + v.name() + ")";
            return new SimpleStringProperty(d);
        });

        colQty.setPrefWidth(78);
        colQty.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colQty.setCellFactory(col -> new EditableQuantityCell(this, col));

        colPrice.setPrefWidth(96);
        colPrice.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colPrice.setCellFactory(col -> new EditablePriceCell(this, col));

        colMrp.setPrefWidth(96);
        colMrp.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getVariant().price())));

        colDiscountPct.setPrefWidth(90);
        colDiscountPct.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colDiscountPct.setCellFactory(col -> new EditableDiscountPctCell(this, col));

        colDiscountAmt.setPrefWidth(98);
        colDiscountAmt.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colDiscountAmt.setCellFactory(col -> new EditableDiscountAmtCell(this, col));

        colTotal.setPrefWidth(115);
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getNetLineTotal())));

        cartTable.getTableView().setEditable(true);
        cartTable.getTableView().getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        cartTable.getTableView().getSelectionModel().setCellSelectionEnabled(true);
        cartTable.setEmptyMessage("Your cart is empty");
        cartTable.setEmptySubtitle("Search for products or scan a barcode to add items.");
        cartTable.getTableView().setFixedCellSize(60);

        cartTable.getTableView().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                CartItem sel = cartTable.getTableView().getSelectionModel().getSelectedItem();
                if (sel != null) { currentBill.getItems().remove(sel); refreshCurrentBill(); e.consume(); }
            } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE
                    || (!e.isControlDown() && !e.isAltDown() && e.getText().length() > 0)) {
                @SuppressWarnings("unchecked")
                TablePosition<CartItem, ?> pos = cartTable.getTableView().getFocusModel().getFocusedCell();
                if (pos != null && pos.getTableColumn() != null) {
                    TableColumn<CartItem, ?> col = pos.getTableColumn();
                    if (col == colQty || col == colPrice || col == colDiscountAmt || col == colDiscountPct) {
                        cartTable.getTableView().edit(pos.getRow(), col); e.consume();
                    }
                }
            }
        });
    }

    // ── Combos / Toggles / Listeners ─────────────────────────────────────────

    private void loadCombos() {
        customerCombo.setConverter(new javafx.util.StringConverter<Customer>() {
            @Override public String toString(Customer c) { return c == null ? "Walk-in Customer" : c.name() + " (" + c.phone() + ")"; }
            @Override public Customer fromString(String s) { return null; }
        });
        try { customerCombo.getItems().setAll(salesService.getAllCustomers()); } catch (Exception ignored) {}

        paymentMethodCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null || empty ? "Select Method" : item.name());
            }
        });
        paymentMethodCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null || empty ? "Select Method" : item.name());
            }
        });
        List<PaymentMethod> methods = salesService.getPaymentMethods();
        paymentMethodCombo.getItems().setAll(methods);
        if (!methods.isEmpty()) paymentMethodCombo.setValue(methods.get(0));
    }

    private void setupBillingToggles() {
        ToggleGroup pg = new ToggleGroup();
        btnFullPayment.setToggleGroup(pg); btnPartialPayment.setToggleGroup(pg);
        ToggleGroup dg = new ToggleGroup();
        btnDiscountFixed.setToggleGroup(dg); btnDiscountPercent.setToggleGroup(dg);
        btnDiscountFixed.setText(CurrencyUtil.getSymbol());

        pg.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (isAutofilling) return;
            if (newV == null) { oldV.setSelected(true); return; }
            currentBill.setFullPayment(newV == btnFullPayment);
            updateBalanceLabel(); refreshCurrentBill();
        });
        dg.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (isAutofilling) return;
            if (newV == null) { oldV.setSelected(true); return; }
            currentBill.setDiscountFixed(newV == btnDiscountFixed);
            recalculateTotals();
        });
    }

    private void setupListeners() {
        searchField.setOnAction(e -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) return;
            Optional<Variant> barcode = searchIndex.findByBarcode(q);
            if (barcode.isPresent()) { addToCart(barcode.get()); searchField.clear(); autocomplete.getSearchPopup().hide(); return; }
            if (autocomplete.getSearchPopup().isShowing() && !autocomplete.getSearchResultsView().getItems().isEmpty()) {
                addToCart(autocomplete.getSearchResultsView().getItems().get(0)); searchField.clear(); autocomplete.getSearchPopup().hide();
            } else {
                List<Variant> res = searchIndex.searchByName(q);
                if (!res.isEmpty()) { addToCart(res.get(0)); searchField.clear(); }
                else NotificationService.warning("No product found");
            }
        });
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && !cartTable.getTableView().getItems().isEmpty()) {
                cartTable.getTableView().requestFocus();
                cartTable.getTableView().getSelectionModel().select(0, colSku);
                cartTable.getTableView().getFocusModel().focus(0, colSku);
            }
        });
        searchField.setOnMouseClicked(e -> autocomplete.showSearchPopup(searchField, searchField.getText() != null ? searchField.getText().trim() : ""));

        discountField.textProperty().addListener((obs, o, n) -> {
            if (isAutofilling) return;
            try { currentBill.setOverallDiscountValue(n.isEmpty() ? BigDecimal.ZERO : new BigDecimal(n)); recalculateTotals(); } catch (Exception ignored) {}
        });
        tenderedField.textProperty().addListener((obs, o, n) -> {
            if (isAutofilling) return;
            try { currentBill.setAmountTendered(n.isEmpty() ? BigDecimal.ZERO : new BigDecimal(n)); updateBalanceLabel(); saveCurrentDraft(); } catch (Exception ignored) {}
        });
        customerCombo.valueProperty().addListener((obs, old, val) -> {
            if (isAutofilling) return;
            isAutofilling = true;
            try {
                currentBill.setSelectedCustomer(val);
                if (val != null) {
                    customerNameField.setText(val.name()); customerPhoneField.setText(val.phone());
                    customerEmailField.setText(val.email() != null ? val.email() : "");
                    customerAddressField.setText(val.address() != null ? val.address() : "");
                } else if (old != null && old.name().equals(customerNameField.getText().trim())
                        && old.phone().equals(customerPhoneField.getText().trim())) {
                    System.out.println("DEBUG: Clearing customer fields because combo selection was removed");
                    customerNameField.clear(); customerPhoneField.clear(); customerEmailField.clear(); customerAddressField.clear();
                }
            } finally { isAutofilling = false; }
        });
        customerNameField.textProperty().addListener((o, oldV, n) -> { if (!isAutofilling) { currentBill.setCustomerName(n); checkCustomerMatch(); recalculateTotals(); } });
        customerPhoneField.textProperty().addListener((o, oldV, n) -> { if (!isAutofilling) { currentBill.setCustomerPhone(n); checkCustomerMatch(); recalculateTotals(); } });
        customerEmailField.textProperty().addListener((o, oldV, n) -> { if (!isAutofilling) { currentBill.setCustomerEmail(n); saveCurrentDraft(); } });
        customerAddressField.textProperty().addListener((o, oldV, n) -> { if (!isAutofilling) { currentBill.setCustomerAddress(n); recalculateTotals(); } });
        paymentMethodCombo.valueProperty().addListener((o, oldV, n) -> { 
            if (isAutofilling) return;
            currentBill.setSelectedPaymentMethod(n); updateBalanceLabel(); saveCurrentDraft(); 
        });
    }

    // ── Keyboard Shortcuts ────────────────────────────────────────────────────

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (rootPane == null || rootPane.getScene() == null) return;
            rootPane.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                boolean cmd = e.isControlDown() || e.isMetaDown();
                if (cmd && e.getCode() == KeyCode.K) { focusSearch(true); e.consume(); return; }
                if (cmd && e.getCode() == KeyCode.ENTER) { if (!completeButton.isDisabled()) handleCompleteSale(); e.consume(); return; }
                if (cmd && e.getCode() == KeyCode.N) { openOrSwitchToNextBill(); e.consume(); return; }
                if (cmd && e.getCode().isDigitKey()) {
                    String t = e.getText();
                    if (t != null && !t.isEmpty()) { int d = t.charAt(0) - '1'; if (d >= 0 && d < MAX_BILLS) { switchBill(d); e.consume(); } }
                    return;
                }
                if (e.getCode() == KeyCode.F12) {
                    if (currentBill.getItems().isEmpty()) { NotificationService.warning("Add items to cart first"); e.consume(); return; }
                    if (tenderedField.isFocused()) { if (!completeButton.isDisabled()) handleCompleteSale(); }
                    else { tenderedField.requestFocus(); tenderedField.selectAll(); }
                    e.consume(); return;
                }
                if (e.getCode() == KeyCode.ESCAPE) { autocomplete.getSearchPopup().hide(); autocomplete.getQuickProductPopup().hide(); autocomplete.getQuickCategoryPopup().hide(); rootPane.requestFocus(); e.consume(); }
            });
        });
    }

    private void setupLayoutSizing() {
        // Relying on FXML hgrow for sizing to avoid layout pass conflicts
    }

    // ── Bill Management ───────────────────────────────────────────────────────

    private void renderBillsFlowPane() {
        Platform.runLater(() -> {
            if (billsFlowPane == null) return;
            List<javafx.scene.Node> children = billsFlowPane.getChildren();
            int count = Math.min(MAX_BILLS, bills.size());
            
            // Re-use or populate initially to avoid modifying scene graph during layout
            if (children.isEmpty()) {
                for (int i = 0; i < MAX_BILLS; i++) {
                    Button btn = new Button(String.valueOf(i + 1));
                    btn.setCursor(javafx.scene.Cursor.HAND);
                    int idx = i; 
                    btn.setOnAction(e -> switchBill(idx));
                    children.add(btn);
                }
            }

            for (int i = 0; i < count; i++) {
                if (i >= children.size()) break;
                SaleDraft bill = bills.get(i);
                Button btn = (Button) children.get(i);
                
                boolean active = currentBill != null && currentBill.getIndex() == i;
                boolean hasItems = !bill.getItems().isEmpty();
                
                String style = "-fx-font-weight: bold; -fx-background-radius: 8; -fx-border-radius: 8; "
                             + "-fx-min-width: 34; -fx-min-height: 34; -fx-font-size: 13px; -fx-padding: 0; ";
                if (active) {
                    style += "-fx-background-color: #0f172a; -fx-text-fill: white; -fx-border-color: #0f172a; -fx-border-width: 1.5;";
                } else if (hasItems) {
                    style += "-fx-background-color: #fef3c7; -fx-text-fill: #d97706; -fx-border-color: #fcd34d; -fx-border-width: 1;";
                } else {
                    style += "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-border-color: #e2e8f0; -fx-border-width: 1;";
                }
                btn.setStyle(style); 
            }
        });
    }

    private void switchBill(int index) {
        currentBill = bills.get(index);
        isAutofilling = true;
        try {
            btnFullPayment.setSelected(currentBill.isFullPayment());
            btnPartialPayment.setSelected(!currentBill.isFullPayment());
            discountField.setText(currentBill.getOverallDiscountValue().compareTo(BigDecimal.ZERO) > 0 ? currentBill.getOverallDiscountValue().toString() : "");
            btnDiscountFixed.setSelected(currentBill.isDiscountFixed()); btnDiscountPercent.setSelected(!currentBill.isDiscountFixed());
            tenderedField.setText(currentBill.getAmountTendered().compareTo(BigDecimal.ZERO) > 0 ? currentBill.getAmountTendered().toString() : "");
            
            Customer sel = currentBill.getSelectedCustomer();
            customerCombo.setValue(sel);
            
            String name = currentBill.getCustomerName();
            String phone = currentBill.getCustomerPhone();
            
            // If we have a selected customer but the manual fields are empty (e.g. first load after migration)
            if (sel != null && (name == null || name.isEmpty())) {
                name = sel.name();
                phone = sel.phone();
                currentBill.setCustomerName(name);
                currentBill.setCustomerPhone(phone);
                currentBill.setCustomerEmail(sel.email());
                currentBill.setCustomerAddress(sel.address());
            }

            customerNameField.setText(name != null ? name : "");
            customerPhoneField.setText(phone != null ? phone : "");
            customerEmailField.setText(currentBill.getCustomerEmail() != null ? currentBill.getCustomerEmail() : "");
            customerAddressField.setText(currentBill.getCustomerAddress() != null ? currentBill.getCustomerAddress() : "");
            
            paymentMethodCombo.setValue(currentBill.getSelectedPaymentMethod());
            if (currentBill.getSelectedPaymentMethod() == null && !paymentMethodCombo.getItems().isEmpty()) {
                currentBill.setSelectedPaymentMethod(paymentMethodCombo.getItems().get(0));
                paymentMethodCombo.setValue(currentBill.getSelectedPaymentMethod());
            }
        } finally { isAutofilling = false; }
        
        refreshCurrentBill(); focusSearch(false);
    }

    private void openOrSwitchToNextBill() {
        for (SaleDraft b : bills) if (b.getItems().isEmpty() && b.getIndex() != currentBill.getIndex()) { switchBill(b.getIndex()); return; }
        int next = (currentBill.getIndex() + 1) % MAX_BILLS;
        if (next == currentBill.getIndex()) return;
        bills.get(next).reset(); switchBill(next);
        NotificationService.info("Switched to a fresh bill tab");
    }

    // ── Cart Mutations ────────────────────────────────────────────────────────

    private void addToCart(Variant variant) {
        Optional<CartItem> exists = currentBill.getItems().stream().filter(it -> it.getVariant().id().equals(variant.id())).findFirst();
        int nQty = exists.map(it -> it.getQuantity() + 1).orElse(1);
        if (isInventoryRestrictionsEnabled() && variant.stock() != null && nQty > variant.stock()) {
            NotificationService.warning("Insufficient stock! Available: " + variant.stock()); return;
        }
        if (exists.isPresent()) exists.get().setQuantity(nQty);
        else currentBill.getItems().add(new CartItem(variant, 1));
        refreshCurrentBill();
        final CartItem target = exists.orElse(currentBill.getItems().get(currentBill.getItems().size() - 1));
        Platform.runLater(() -> {
            int i = currentBill.getItems().indexOf(target);
            if (i >= 0) { cartTable.getTableView().getSelectionModel().select(i, colQty); cartTable.getTableView().getFocusModel().focus(i, colQty); cartTable.getTableView().edit(i, colQty); }
        });
    }

    public void refreshCurrentBill() { 
        if (currentBill != null && cartTable != null && cartTable.getTableView().getItems() != null) {
            cartTable.getTableView().getItems().setAll(currentBill.getItems());
        }
        renderBillsFlowPane(); 
        updatePaymentSectionState(); 
        recalculateTotals(); 
        saveCurrentDraft();
    }

    private void saveCurrentDraft() {
        if (isAutofilling) return;
        posDraftRepository.saveBill(currentBill);
    }

    @FXML private void handleClearCart() { 
        posDraftRepository.deleteBill(currentBill.getIndex());
        currentBill.reset(); 
        refreshCurrentBill(); 
        switchBill(currentBill.getIndex()); 
        NotificationService.info("Cart cleared");
    }

    @FXML private void handleResetCustomer() {
        isAutofilling = true;
        try {
            customerCombo.setValue(null); customerNameField.clear(); customerPhoneField.clear(); customerEmailField.clear(); customerAddressField.clear();
            currentBill.setSelectedCustomer(null); currentBill.setCustomerName(""); currentBill.setCustomerPhone(""); currentBill.setCustomerEmail(""); currentBill.setCustomerAddress("");
            recalculateTotals();
            NotificationService.info("Customer selection reset");
        } finally { isAutofilling = false; }
    }

    @FXML private void handleCompleteSale() { 
        completionHandler.execute(currentBill, completeButton); 
        // Note: completionHandler should call a callback to delete the draft on success
    }

    @FXML private void handleQuickAddProduct() {
        String pN = quickProductName.getText().trim(), vN = quickVariantName.getText().trim();
        String pS = quickPrice.getText().trim(),       sS = quickStock.getText().trim();
        String cN = quickCategorySearch.getText().trim();
        if (pN.isEmpty() || pS.isEmpty() || cN.isEmpty()) { NotificationService.error("Please enter product name, price and select a category."); return; }
        Category cat = selectedCategoryForQuickAdd;
        if (cat == null) cat = categoryService.getAllCategories().stream().filter(c -> c.name().equalsIgnoreCase(cN)).findFirst().orElse(null);
        if (cat == null) { NotificationService.error("Please select a valid category."); return; }
        try {
            BigDecimal price = new BigDecimal(pS); int stock = sS.isEmpty() ? 1 : Math.max(0, Integer.parseInt(sS));
            AuthUser cur = AuthContext.getCurrentUser(); long uId = cur != null ? cur.id() : 1L;
            Variant vCart = null; Long pId = selectedProductIdForQuickAdd;
            if (pId == null) {
                Optional<Variant> m = searchIndex.searchByName(pN).stream().filter(v -> v.productName().equalsIgnoreCase(pN)).findFirst();
                if (m.isPresent()) pId = m.get().productId();
            }
            final Category finalCat = cat;
            if (pId != null) {
                final String fvN = vN; final Long fId = pId;
                Optional<Variant> ex = searchIndex.searchByName(pN).stream().filter(v -> v.productId().equals(fId) && v.name().equalsIgnoreCase(fvN)).findFirst();
                if (ex.isPresent()) {
                    vCart = ex.get();
                    productService.updateProduct(fId, new ProductService.UpdateProductCommand(null, null, null, null, null, List.of(new ProductService.VariantCommand(vCart.id(), vCart.name(), vCart.sku(), price, vCart.costPrice(), vCart.stockAlertCap(), vCart.defaultVariant(), vCart.status(), stock, "Quick add adjustment")), null, uId));
                } else {
                    productService.updateProduct(fId, new ProductService.UpdateProductCommand(null, null, null, null, null, List.of(new ProductService.VariantCommand(null, vN, null, price, BigDecimal.ZERO, 0, false, "active", stock, null)), null, uId));
                }
                searchIndex.refresh();
                final String fvN2 = vN; vCart = searchIndex.searchByName(pN).stream().filter(v -> v.productId().equals(fId) && v.name().equalsIgnoreCase(fvN2)).findFirst().orElse(null);
            } else {
                productService.createProductWithVariants(new ProductService.CreateProductCommand(pN, "Quick added from POS", finalCat.id(), "active", null, List.of(new ProductService.VariantCommand(null, vN, null, price, BigDecimal.ZERO, 0, true, "active", stock, null)), null, uId));
                searchIndex.refresh(); final String fvN = vN;
                vCart = searchIndex.searchByName(pN).stream().filter(v -> v.productName().equalsIgnoreCase(pN) && v.name().equalsIgnoreCase(fvN)).findFirst().orElse(null);
            }
            if (vCart != null) {
                addToCart(vCart); quickProductName.clear(); quickVariantName.clear(); quickPrice.clear(); quickStock.clear(); quickCategorySearch.clear();
                selectedProductIdForQuickAdd = null; selectedCategoryForQuickAdd = null; NotificationService.success("Added to cart.");
            } else NotificationService.error("Failed to process quick add.");
        } catch (NumberFormatException e) { NotificationService.error("Please enter a valid numeric price/stock."); }
        catch (Exception e) { LoggingConfig.getLogger().error("Quick add failed", e); NotificationService.error("Quick add failed: " + ErrorHandler.toUserMessage(e)); }
    }

    // ── Totals / UI ───────────────────────────────────────────────────────────

    private void recalculateTotals() { saleCalculator.recalculate(currentBill); updateUI(); }

    private void updateUI() {
        subtotalLabel.setText(CurrencyUtil.format(currentBill.getSubtotal()));
        totalDiscountLabel.setText(CurrencyUtil.format(currentBill.getDiscountTotal()));
        taxLabel.setText(CurrencyUtil.format(currentBill.getTaxAmount()));
        totalLabel.setText(CurrencyUtil.format(currentBill.getTotal()));
        bottomTotalLabel.setText(CurrencyUtil.format(currentBill.getTotal()));
        bottomMrpLabel.setText(CurrencyUtil.format(currentBill.getTotalMrp()));
        bottomPriceTotalLabel.setText(CurrencyUtil.format(currentBill.getTotalPrice()));
        totalQtyLabel.setText(String.valueOf(currentBill.getItems().stream().mapToInt(CartItem::getQuantity).sum()));
        updateBalanceLabel();
        saveCurrentDraft();
    }

    private void updatePaymentSectionState() {
        boolean has = currentBill != null && !currentBill.getItems().isEmpty();
        if (paymentCard != null) { paymentCard.setDisable(!has); paymentCard.setOpacity(has ? 1.0 : 0.52); }
    }

    private void updateBalanceLabel() {
        BigDecimal diff = currentBill.getAmountTendered().subtract(currentBill.getTotal());
        if (diff.compareTo(BigDecimal.ZERO) >= 0) {
            balanceTypeLabel.setText("Change"); balanceTypeLabel.setTextFill(Color.web("#16a34a"));
            balanceLabel.setText(CurrencyUtil.format(diff));
            balanceLabel.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #16a34a; -fx-border-color: #bbf7d0; -fx-border-radius: 6; -fx-padding: 7 10; -fx-font-weight: bold; -fx-font-size: 14px;");
        } else {
            balanceTypeLabel.setText("Balance"); balanceTypeLabel.setTextFill(Color.web("#64748b"));
            balanceLabel.setText(CurrencyUtil.format(diff.abs()));
            balanceLabel.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-border-color: #e2e8f0; -fx-border-radius: 6; -fx-padding: 7 10; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
        if (!currentBill.getItems().isEmpty() && currentBill.getSelectedPaymentMethod() != null) {
            boolean valid = currentBill.isFullPayment() ? diff.compareTo(BigDecimal.ZERO) >= 0
                    : (currentBill.getAmountTendered().compareTo(BigDecimal.ZERO) > 0 && currentBill.getAmountTendered().compareTo(currentBill.getTotal()) < 0);
            completeButton.setDisable(!valid);
        } else completeButton.setDisable(true);
        updateTenderedVisibility();
    }

    private void updateTenderedVisibility() {
        if (tenderedBalanceContainer == null) return;
        PaymentMethod m = currentBill.getSelectedPaymentMethod(); boolean fp = currentBill.isFullPayment();
        if (m == null) { tenderedBalanceContainer.setVisible(true); tenderedBalanceContainer.setManaged(true); return; }
        String n = m.name().toUpperCase(); boolean isDigital = n.contains("DEBIT") || n.contains("CREDIT") || n.contains("UPI") || n.contains("CARD");
        boolean hide = isDigital && fp; tenderedBalanceContainer.setVisible(!hide); tenderedBalanceContainer.setManaged(!hide);
        if (hide) currentBill.setAmountTendered(currentBill.getTotal());
    }

    // ── Quick-Add Selection Helpers ───────────────────────────────────────────

    private void selectProductForQuickAdd(Variant v) {
        isAutofilling = true;
        try {
            quickProductName.setText(v.productName()); selectedProductIdForQuickAdd = v.productId();
            if (v.categoryName() != null)
                categoryService.getAllCategories().stream().filter(c -> c.name().equalsIgnoreCase(v.categoryName())).findFirst().ifPresent(this::selectCategoryForQuickAdd);
            quickVariantName.requestFocus();
        } finally { isAutofilling = false; }
    }

    private void selectCategoryForQuickAdd(Category c) {
        isAutofilling = true;
        try { quickCategorySearch.setText(c.name()); selectedCategoryForQuickAdd = c; } finally { isAutofilling = false; }
    }

    // ── CartCellHandler Implementation ────────────────────────────────────────

    public void moveFocusNext(int row, TableColumn<CartItem, ?> cur) {
        Platform.runLater(() -> {
            if (cur == colQty)         { cartTable.getTableView().getSelectionModel().select(row, colPrice);        cartTable.getTableView().edit(row, colPrice); }
            else if (cur == colPrice)  { cartTable.getTableView().getSelectionModel().select(row, colDiscountPct);  cartTable.getTableView().edit(row, colDiscountPct); }
            else if (cur == colDiscountPct) { cartTable.getTableView().getSelectionModel().select(row, colDiscountAmt); cartTable.getTableView().edit(row, colDiscountAmt); }
            else if (cur == colDiscountAmt) { searchField.requestFocus(); searchField.selectAll(); }
        });
    }

    public void moveToNext() {
        @SuppressWarnings("unchecked")
        TablePosition<CartItem, ?> pos = cartTable.getTableView().getFocusModel().getFocusedCell();
        if (pos == null) return;
        int c = cartTable.getTableView().getVisibleLeafIndex(pos.getTableColumn());
        if (c < cartTable.getTableView().getVisibleLeafColumns().size() - 1) {
            cartTable.getTableView().getSelectionModel().select(pos.getRow(), cartTable.getTableView().getVisibleLeafColumn(c + 1));
            cartTable.getTableView().edit(pos.getRow(), cartTable.getTableView().getVisibleLeafColumn(c + 1));
        } else if (pos.getRow() < cartTable.getTableView().getItems().size() - 1) {
            cartTable.getTableView().getSelectionModel().select(pos.getRow() + 1, cartTable.getTableView().getVisibleLeafColumn(0));
            cartTable.getTableView().edit(pos.getRow() + 1, cartTable.getTableView().getVisibleLeafColumn(0));
        }
    }

    public void moveToPrevious() {
        @SuppressWarnings("unchecked")
        TablePosition<CartItem, ?> pos = cartTable.getTableView().getFocusModel().getFocusedCell();
        if (pos == null) return;
        int c = cartTable.getTableView().getVisibleLeafIndex(pos.getTableColumn());
        if (c > 0) {
            cartTable.getTableView().getSelectionModel().select(pos.getRow(), cartTable.getTableView().getVisibleLeafColumn(c - 1));
            cartTable.getTableView().edit(pos.getRow(), cartTable.getTableView().getVisibleLeafColumn(c - 1));
        } else if (pos.getRow() > 0) {
            int last = cartTable.getTableView().getVisibleLeafColumns().size() - 1;
            cartTable.getTableView().getSelectionModel().select(pos.getRow() - 1, cartTable.getTableView().getVisibleLeafColumn(last));
            cartTable.getTableView().edit(pos.getRow() - 1, cartTable.getTableView().getVisibleLeafColumn(last));
        }
    }

    // ── Misc Helpers ──────────────────────────────────────────────────────────

    private void focusSearch(boolean openPopup) {
        searchField.requestFocus(); searchField.selectAll();
        if (openPopup) autocomplete.showSearchPopup(searchField, searchField.getText() != null ? searchField.getText().trim() : "");
    }

    private void checkCustomerMatch() {
        if (isAutofilling) return;
        Customer sel = customerCombo.getValue();
        if (sel != null) {
            String n = customerNameField.getText().trim(), p = customerPhoneField.getText().trim();
            if (!n.equalsIgnoreCase(sel.name()) || !p.equals(sel.phone()))
                Platform.runLater(() -> { if (customerCombo.getValue() != null) customerCombo.setValue(null); });
        }
    }

    public boolean isInventoryRestrictionsEnabled() {
        try { return settingsStore.loadGeneralSettings().isInventoryAlertsAndRestrictionsEnabled(); }
        catch (Exception ex) { return true; }
    }
}

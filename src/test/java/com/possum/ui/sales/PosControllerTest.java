package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.categories.CategoryService;
import com.possum.application.people.CustomerService;
import com.possum.application.products.ProductService;
import com.possum.application.sales.SalesService;
import com.possum.application.sales.TaxEngine;
import com.possum.domain.model.*;
import com.possum.domain.services.SaleCalculator;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.printing.PrinterService;
import com.possum.persistence.repositories.sqlite.SqlitePosDraftRepository;
import com.possum.shared.dto.GeneralSettings;
import com.possum.ui.JavaFXInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SalesService salesService;
    @Mock private CustomerService customerService;
    @Mock private ProductSearchIndex searchIndex;
    @Mock private TaxEngine taxEngine;
    @Mock private PrinterService printerService;
    @Mock private SettingsStore settingsStore;
    @Mock private ProductService productService;
    @Mock private CategoryService categoryService;
    @Mock private SaleCalculator saleCalculator;
    @Mock private SqlitePosDraftRepository posDraftRepository;

    private PosController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("sales:create")));
        controller = new PosController(salesService, customerService, searchIndex, taxEngine,
                printerService, settingsStore, productService, categoryService, saleCalculator, posDraftRepository);
        
        // Use reflection to initialize the controller fields as initialize() is hard to run in JUnit
        java.lang.reflect.Field billsField = PosController.class.getDeclaredField("bills");
        billsField.setAccessible(true);
        List<SaleDraft> bills = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            SaleDraft d = new SaleDraft();
            d.setIndex(i);
            bills.add(d);
        }
        billsField.set(controller, bills);
        
        java.lang.reflect.Field currentBillField = PosController.class.getDeclaredField("currentBill");
        currentBillField.setAccessible(true);
        currentBillField.set(controller, bills.get(0));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should add variant to cart")
    void addToCart_success() throws Exception {
        Variant variant = createTestVariant(1L, "Test Product", "Standard", "123", new BigDecimal("100.00"), 10);
        
        GeneralSettings settings = new GeneralSettings();
        settings.setInventoryAlertsAndRestrictionsEnabled(false);
        when(settingsStore.loadGeneralSettings()).thenReturn(settings);

        java.lang.reflect.Method addToCartMethod = PosController.class.getDeclaredMethod("addToCart", Variant.class);
        addToCartMethod.setAccessible(true);
        
        try {
            addToCartMethod.invoke(controller, variant);
        } catch (Exception e) {}

        java.lang.reflect.Field currentBillField = PosController.class.getDeclaredField("currentBill");
        currentBillField.setAccessible(true);
        SaleDraft currentBill = (SaleDraft) currentBillField.get(controller);
        
        assertEquals(1, currentBill.getItems().size());
        assertEquals(variant.sku(), currentBill.getItems().get(0).getVariant().sku());
    }

    @Test
    @DisplayName("Should recalculate totals correctly")
    void recalculateTotals_success() throws Exception {
        java.lang.reflect.Field currentBillField = PosController.class.getDeclaredField("currentBill");
        currentBillField.setAccessible(true);
        SaleDraft currentBill = (SaleDraft) currentBillField.get(controller);
        
        Variant variant = createTestVariant(1L, "Test Product", "Standard", "123", new BigDecimal("100.00"), 10);
        CartItem cartItem = new CartItem(variant, 2);
        currentBill.getItems().add(cartItem);

        // Recalculate is now delegated to saleCalculator
        doAnswer(invocation -> {
            SaleDraft d = invocation.getArgument(0);
            d.setTotal(new BigDecimal("200.00"));
            return null;
        }).when(saleCalculator).recalculate(any(SaleDraft.class));

        java.lang.reflect.Method recalculateMethod = PosController.class.getDeclaredMethod("recalculateTotals");
        recalculateMethod.setAccessible(true);
        
        try {
            recalculateMethod.invoke(controller);
        } catch (Exception e) {}

        assertEquals(new BigDecimal("200.00"), currentBill.getTotal());
        verify(saleCalculator, times(1)).recalculate(currentBill);
    }

    private Variant createTestVariant(Long id, String productName, String variantName, 
                                     String sku, BigDecimal price, Integer stock) {
        return new Variant(
            id, 1L, productName, variantName, sku, price, BigDecimal.ZERO,
            0, true, "active", null, stock, "Electronics", null, null, null, null
        );
    }

    private Customer createTestCustomer(Long id, String name, String phone) {
        return new Customer(id, name, phone, null, null, "retail", false, null, null, null);
    }
}

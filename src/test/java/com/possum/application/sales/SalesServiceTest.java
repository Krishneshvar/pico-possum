package com.possum.application.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.dto.*;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.domain.repositories.SalesRepository;
import com.possum.domain.repositories.VariantRepository;
import com.possum.domain.repositories.ProductRepository;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.domain.repositories.AuditRepository;
import com.possum.application.sales.InvoiceNumberService;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock private SalesRepository salesRepository;
    @Mock private VariantRepository variantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private InventoryService inventoryService;
    @Mock private TaxEngine taxEngine;
    @Mock private PaymentService paymentService;
    @Mock private TransactionManager transactionManager;
    @Mock private JsonService jsonService;
    @Mock private SettingsStore settingsStore;
    @Mock private InvoiceNumberService invoiceNumberService;

    private SalesService salesService;

    @BeforeEach
    void setUp() {
        salesService = new SalesService(salesRepository, variantRepository, productRepository, customerRepository, auditRepository, inventoryService, taxEngine, new com.possum.domain.services.SaleCalculator(taxEngine), paymentService, transactionManager, jsonService, settingsStore, invoiceNumberService);
        AuthContext.setCurrentUser(new AuthUser(1L, "Cashier", "cashier", List.of(), List.of("sales.create", "sales.manage")));

        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should create sale with valid items and payments")
    void createSale_success() {
        CreateSaleItemRequest itemReq = new CreateSaleItemRequest(10L, 2, new BigDecimal("50.00"), BigDecimal.ZERO);
        PaymentRequest payReq = new PaymentRequest(new BigDecimal("100.00"), 1L);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemReq), null, BigDecimal.ZERO, List.of(payReq));

        Variant v = new Variant(10L, 1L, "Product", "Variant", "SKU1", new BigDecimal("50.00"), new BigDecimal("40.00"), 5, true, "active", null, 100, null, null, LocalDateTime.now(), null, null);
        Product p = new Product(1L, "Product", null, 1L, null, null, null, "active", null, 100, LocalDateTime.now(), null, null);
        
        lenient().when(variantRepository.findVariantByIdSync(10L)).thenReturn(Optional.of(v));
        lenient().when(productRepository.findProductById(1L)).thenReturn(Optional.of(p));
        lenient().when(inventoryService.getVariantStock(10L)).thenReturn(100);
        lenient().when(invoiceNumberService.generate(anyLong())).thenReturn("INV-001");
        lenient().when(salesRepository.insertSale(any())).thenReturn(1L);
        lenient().when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(mock(Sale.class)));

        TaxCalculationResult taxResult = mock(TaxCalculationResult.class);
        lenient().when(taxResult.grandTotal()).thenReturn(new BigDecimal("100.00"));
        lenient().when(taxResult.totalTax()).thenReturn(BigDecimal.ZERO);
        lenient().when(taxResult.getItemByIndex(0)).thenReturn(mock(TaxableItem.class));
        lenient().when(taxEngine.calculate(any(), any())).thenReturn(taxResult);

        SaleResponse response = salesService.createSale(request, 1L);

        assertNotNull(response);
        verify(salesRepository).insertSale(argThat(s -> s.invoiceNumber().equals("INV-001")));
        verify(inventoryService).deductStock(eq(10L), eq(2), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Should block sale if stock is insufficient")
    void createSale_insufficientStock_fail() {
        CreateSaleItemRequest itemReq = new CreateSaleItemRequest(10L, 10, null, null);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemReq), null, null, List.of(new PaymentRequest(BigDecimal.TEN, 1L)));
        
        Variant v = new Variant(10L, 1L, null, "V", "S", BigDecimal.TEN, BigDecimal.ONE, 1, true, "active", null, 5, null, null, LocalDateTime.now(), null, null);
        lenient().when(variantRepository.findVariantByIdSync(10L)).thenReturn(Optional.of(v));
        lenient().when(inventoryService.getVariantStock(10L)).thenReturn(5);

        TaxCalculationResult taxResult = mock(TaxCalculationResult.class);
        lenient().when(taxResult.grandTotal()).thenReturn(new BigDecimal("100.00"));
        lenient().when(taxResult.totalTax()).thenReturn(BigDecimal.ZERO);
        lenient().when(taxResult.getItemByIndex(0)).thenReturn(mock(TaxableItem.class));
        lenient().when(taxEngine.calculate(any(), any())).thenReturn(taxResult);

        assertThrows(InsufficientStockException.class, () -> salesService.createSale(request, 1L));
    }

    @Test
    @DisplayName("Should restore stock when sale is cancelled")
    void cancelSale_restoresStock() {
        Sale sale = new Sale(1L, "INV-1", LocalDateTime.now(), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", null, 1L, null, null, null, null, null, null);
        SaleItem item = new SaleItem(100L, 1L, 10L, "V", "S", "P", 2, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, null);
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of(item));

        salesService.cancelSale(1L, 1L);

        verify(inventoryService).restoreStock(eq(10L), any(), eq(100L), eq(2), anyLong(), eq(InventoryReason.CORRECTION), any(), eq(1L));
        verify(salesRepository).updateSaleStatus(1L, "cancelled");
    }
}

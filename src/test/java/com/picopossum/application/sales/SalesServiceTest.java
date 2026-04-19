package com.picopossum.application.sales;

import com.picopossum.application.auth.AuthContext;
import com.picopossum.application.auth.AuthUser;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.sales.dto.*;
import com.picopossum.domain.model.*;
import com.picopossum.domain.repositories.*;
import com.picopossum.domain.services.SaleCalculator;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    @Mock private SalesRepository salesRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private InventoryService inventoryService;
    @Mock private SaleCalculator saleCalculator;
    @Mock private PaymentService paymentService;
    @Mock private TransactionManager transactionManager;
    @Mock private JsonService jsonService;
    @Mock private SettingsStore settingsStore;
    @Mock private InvoiceNumberService invoiceNumberService;

    private SalesService salesService;

    @BeforeEach
    void setUp() {
        salesService = new SalesService(
            salesRepository, productRepository, customerRepository, auditRepository,
            inventoryService, saleCalculator, paymentService,
            transactionManager, jsonService, settingsStore, invoiceNumberService
        );
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
    @DisplayName("Should fetch sale details correctly")
    void getSaleDetails_success() {
        Sale sale = new Sale(1L, "INV-001", null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "paid", "fulfilled", null, 1L, null, null, null, null, null, null);
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of());
        when(salesRepository.findTransactionsBySaleId(1L)).thenReturn(List.of());

        SaleResponse response = salesService.getSaleDetails(1L);

        assertNotNull(response);
        assertEquals(sale, response.sale());
        verify(salesRepository).findSaleById(1L);
    }

    @Test
    @DisplayName("Should throw NotFound if sale does not exist")
    void getSaleDetails_notFound_fail() {
        when(salesRepository.findSaleById(99L)).thenReturn(Optional.empty());
        assertThrows(com.picopossum.domain.exceptions.NotFoundException.class, () -> salesService.getSaleDetails(99L));
    }

    @Test
    @DisplayName("Should get all customers")
    void getAllCustomers_success() {
        com.picopossum.shared.dto.PagedResult<Customer> result = new com.picopossum.shared.dto.PagedResult<>(List.of(mock(Customer.class)), 1, 1, 1, 15);
        when(customerRepository.findCustomers(any())).thenReturn(result);

        List<Customer> customers = salesService.getAllCustomers();

        assertEquals(1, customers.size());
        verify(customerRepository).findCustomers(any());
    }

    @Test
    @DisplayName("Should get payment methods")
    void getPaymentMethods_success() {
        when(paymentService.getActivePaymentMethods()).thenReturn(List.of(mock(PaymentMethod.class)));
        List<PaymentMethod> methods = salesService.getPaymentMethods();
        assertEquals(1, methods.size());
    }
}

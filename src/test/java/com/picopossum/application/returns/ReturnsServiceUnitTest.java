package com.picopossum.application.returns;

import com.picopossum.application.audit.AuditService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.returns.dto.CreateReturnItemRequest;
import com.picopossum.application.returns.dto.CreateReturnRequest;
import com.picopossum.application.returns.dto.ReturnResponse;
import com.picopossum.application.sales.InvoiceNumberService;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.domain.repositories.ReturnsRepository;
import com.picopossum.domain.repositories.SalesRepository;
import com.picopossum.domain.services.ReturnCalculator;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnsServiceUnitTest {

    @Mock private ReturnsRepository returnsRepository;
    @Mock private SalesRepository salesRepository;
    @Mock private InventoryService inventoryService;
    @Mock private AuditService auditService;
    @Mock private TransactionManager transactionManager;
    @Mock private JsonService jsonService;
    @Mock private ReturnCalculator returnCalculator;
    @Mock private InvoiceNumberService invoiceNumberService;

    private ReturnsService service;

    @BeforeEach
    void setUp() {
        service = new ReturnsService(returnsRepository, salesRepository, inventoryService,
                auditService, transactionManager, jsonService, returnCalculator, invoiceNumberService);
        
        // Mock transaction manager to execute the supplier immediately
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("Should throw ValidationException for invalid inputs")
    void createReturn_invalidInputs() {
        assertThrows(ValidationException.class, () -> service.createReturn(new CreateReturnRequest(null, List.of(), "")));
        assertThrows(ValidationException.class, () -> service.createReturn(new CreateReturnRequest(1L, List.of(), "reason")));
        assertThrows(ValidationException.class, () -> service.createReturn(new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(1L, 0)), "reason")));
    }

    @Test
    @DisplayName("Should throw NotFoundException if sale doesn't exist")
    void createReturn_saleNotFound() {
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.empty());
        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(1L, 1)), "Reason");
        
        assertThrows(NotFoundException.class, () -> service.createReturn(request));
    }

    @Test
    @DisplayName("Should throw ValidationException if returning more than available")
    void createReturn_exceedsAvailableQuantity() {
        Sale sale = mock(Sale.class);
        SaleItem item = new SaleItem(10L, 1L, 100L, "SKU", "Prod", 5, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of(item));
        when(returnsRepository.getTotalReturnedQuantity(10L)).thenReturn(3); // 5 - 3 = 2 available
        
        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(10L, 3)), "Reason");
        
        ValidationException ex = assertThrows(ValidationException.class, () -> service.createReturn(request));
        assertTrue(ex.getMessage().contains("Only 2 remaining"));
    }

    @Test
    @DisplayName("Should throw ValidationException if refund exceeds paid amount")
    void createReturn_refundExceedsPaidAmount() {
        Sale sale = new Sale(1L, "INV-1", null, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-1");
        SaleItem item = new SaleItem(10L, 1L, 100L, "SKU", "Prod", 5, new BigDecimal("20.00"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of(item));
        when(returnsRepository.getTotalReturnedQuantity(10L)).thenReturn(0);
        
        // Mock calculator to return 60.00 refund (more than 50.00 paid)
        when(returnCalculator.calculateRefunds(anyList(), anyList(), any())).thenReturn(List.of(
            new com.picopossum.application.returns.dto.RefundCalculation(10L, 3, new BigDecimal("60.00"), 100L, new BigDecimal("20.00"), "SKU", "Prod")
        ));
        when(returnCalculator.calculateTotalRefund(anyList())).thenReturn(new BigDecimal("60.00"));
        
        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(10L, 3)), "Reason");
        
        assertThrows(ValidationException.class, () -> service.createReturn(request));
    }

    @Test
    @DisplayName("Should successfully create a partial return")
    void createReturn_success() {
        Sale sale = new Sale(1L, "INV-1", null, new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-1");
        SaleItem item = new SaleItem(10L, 1L, 100L, "SKU", "Prod", 5, new BigDecimal("20.00"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of(item));
        when(returnsRepository.getTotalReturnedQuantity(10L)).thenReturn(0);
        
        BigDecimal refundAmount = new BigDecimal("40.00");
        when(returnCalculator.calculateRefunds(anyList(), anyList(), any())).thenReturn(List.of(
            new com.picopossum.application.returns.dto.RefundCalculation(10L, 2, refundAmount, 100L, new BigDecimal("20.00"), "SKU", "Prod")
        ));
        when(returnCalculator.calculateTotalRefund(anyList())).thenReturn(refundAmount);
        when(invoiceNumberService.generate(eq("R"), anyLong())).thenReturn("R-001");
        when(returnsRepository.insertReturn(any())).thenReturn(500L);
        when(returnsRepository.insertReturnItem(any())).thenReturn(600L);

        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(10L, 2)), "Defective");
        
        ReturnResponse response = service.createReturn(request);
        
        assertEquals(500L, response.id());
        assertEquals(refundAmount, response.totalRefund());
        
        verify(salesRepository).updateSalePaidAmount(eq(1L), argThat(a -> a.compareTo(new BigDecimal("60.00")) == 0));
        verify(salesRepository).updateSaleStatus(1L, "partially_refunded");
        verify(inventoryService).adjustInventory(eq(100L), eq(2), any(), any(), eq(600L), anyString());
        verify(auditService).logCreate(eq("returns"), eq(500L), anyMap());
    }

    @Test
    @DisplayName("Should mark sale as refunded if paid amount reaches zero")
    void createReturn_fullRefund() {
        Sale sale = new Sale(1L, "INV-1", null, new BigDecimal("40.00"), new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-1");
        SaleItem item = new SaleItem(10L, 1L, 100L, "SKU", "Prod", 2, new BigDecimal("20.00"), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        when(salesRepository.findSaleItems(1L)).thenReturn(List.of(item));
        when(returnsRepository.getTotalReturnedQuantity(10L)).thenReturn(0);
        
        BigDecimal refundAmount = new BigDecimal("40.00");
        when(returnCalculator.calculateRefunds(anyList(), anyList(), any())).thenReturn(List.of(
            new com.picopossum.application.returns.dto.RefundCalculation(10L, 2, refundAmount, 100L, new BigDecimal("20.00"), "SKU", "Prod")
        ));
        when(returnCalculator.calculateTotalRefund(anyList())).thenReturn(refundAmount);
        when(invoiceNumberService.generate(anyString(), anyLong())).thenReturn("R-001");

        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(10L, 2)), "Full Refund");
        
        service.createReturn(request);
        
        verify(salesRepository).updateSalePaidAmount(eq(1L), argThat(a -> a.compareTo(BigDecimal.ZERO) == 0));
        verify(salesRepository).updateSaleStatus(1L, "refunded");
    }

    @Test
    @DisplayName("Should throw ValidationException for cancelled sale")
    void createReturn_cancelledSale() {
        Sale sale = new Sale(1L, "INV-1", null, new BigDecimal("40.00"), new BigDecimal("40.00"), BigDecimal.ZERO, BigDecimal.ZERO, "cancelled", "cancelled", null, "Guest", null, null, "System", 1L, "Cash", "INV-1");
        
        when(salesRepository.findSaleById(1L)).thenReturn(Optional.of(sale));
        
        CreateReturnRequest request = new CreateReturnRequest(1L, List.of(new CreateReturnItemRequest(10L, 1)), "Reason");
        
        assertThrows(ValidationException.class, () -> service.createReturn(request));
    }
}

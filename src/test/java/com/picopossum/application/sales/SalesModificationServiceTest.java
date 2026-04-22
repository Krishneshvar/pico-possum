package com.picopossum.application.sales;

import com.picopossum.application.audit.AuditService;
import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.sales.dto.UpdateSaleItemRequest;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.*;
import com.picopossum.domain.repositories.*;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.shared.dto.GeneralSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class SalesModificationServiceTest {

    @Mock private SalesRepository salesRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private AuditService auditService;
    @Mock private InventoryService inventoryService;
    @Mock private TransactionManager transactionManager;
    @Mock private JsonService jsonService;
    @Mock private SettingsStore settingsStore;

    private SalesModificationService service;

    // Fixtures
    private static final long SALE_ID = 1L;
    private static final long PRODUCT_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new SalesModificationService(
            salesRepository, productRepository, customerRepository,
            auditService, inventoryService, transactionManager, jsonService, settingsStore
        );
        // Make transactions execute inline
        lenient().when(transactionManager.runInTransaction(any())).thenAnswer(inv -> {
            Supplier<?> s = inv.getArgument(0);
            return s.get();
        });
        // Default: inventory restrictions disabled
        GeneralSettings gs = mock(GeneralSettings.class);
        lenient().when(gs.isInventoryAlertsAndRestrictionsEnabled()).thenReturn(false);
        lenient().when(settingsStore.loadGeneralSettings()).thenReturn(gs);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Sale paidSale() {
        return new Sale(SALE_ID, "INV-001", null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled",
                null, "Guest", null, null, "System", 1L, "Cash", "INV-001");
    }

    private Sale cancelledSale() {
        return new Sale(SALE_ID, "INV-001", null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "cancelled", "cancelled",
                null, "Guest", null, null, "System", 1L, "Cash", "INV-001");
    }

    private Sale refundedSale() {
        return new Sale(SALE_ID, "INV-001", null, new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "refunded", "fulfilled",
                null, "Guest", null, null, "System", 1L, "Cash", "INV-001");
    }

    private SaleItem saleItem(int qty) {
        return new SaleItem(1L, SALE_ID, PRODUCT_ID, "SKU-10", "Product A",
                qty, new BigDecimal("50.00"), new BigDecimal("30.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    private Product product() {
        return new Product(PRODUCT_ID, "Product A", null, null, null, BigDecimal.ZERO,
                "SKU-10", null, new BigDecimal("50.00"), new BigDecimal("30.00"),
                5, ProductStatus.ACTIVE, null, 0, null, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // cancelSale
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelSale")
    class CancelSale {

        @Test
        @DisplayName("Should cancel a paid sale and restore stock")
        void cancelPaidSale_restoresStock() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(saleItem(2)));

            service.cancelSale(SALE_ID);

            verify(inventoryService).adjustInventory(eq(PRODUCT_ID), eq(2), any(), anyString(), eq(SALE_ID), anyString());
            verify(salesRepository).updateSaleStatus(eq(SALE_ID), eq("cancelled"));
            verify(salesRepository).updateFulfillmentStatus(eq(SALE_ID), eq("cancelled"));
        }

        @Test
        @DisplayName("Should throw ValidationException when cancelling an already-cancelled sale")
        void cancelCancelledSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(cancelledSale()));

            assertThrows(ValidationException.class, () -> service.cancelSale(SALE_ID));
            verify(salesRepository, never()).updateSaleStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("Should throw ValidationException when cancelling a refunded sale")
        void cancelRefundedSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(refundedSale()));

            assertThrows(ValidationException.class, () -> service.cancelSale(SALE_ID));
        }

        @Test
        @DisplayName("Should throw NotFoundException for non-existent sale")
        void cancelNonExistentSale_throwsNotFound() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.empty());
            assertThrows(NotFoundException.class, () -> service.cancelSale(SALE_ID));
        }

        @Test
        @DisplayName("Should restore stock for EACH line item when cancelling multi-item sale")
        void cancelMultiItemSale_restoresEachItem() {
            SaleItem item1 = new SaleItem(1L, SALE_ID, 10L, "SKU-A", "A", 3,
                    new BigDecimal("10.00"), new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
            SaleItem item2 = new SaleItem(2L, SALE_ID, 11L, "SKU-B", "B", 5,
                    new BigDecimal("20.00"), new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);

            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(item1, item2));

            service.cancelSale(SALE_ID);

            verify(inventoryService).adjustInventory(eq(10L), eq(3), any(), any(), eq(SALE_ID), any());
            verify(inventoryService).adjustInventory(eq(11L), eq(5), any(), any(), eq(SALE_ID), any());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // completeSale
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeSale")
    class CompleteSale {

        private Sale pendingSale() {
            return new Sale(SALE_ID, "INV-001", null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, "paid", "pending",
                    null, "Guest", null, null, "System", 1L, "Cash", "INV-001");
        }

        @Test
        @DisplayName("Should mark a pending sale as fulfilled")
        void completePendingSale_success() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(pendingSale()));

            service.completeSale(SALE_ID);

            verify(salesRepository).updateFulfillmentStatus(eq(SALE_ID), eq("fulfilled"));
        }

        @Test
        @DisplayName("Should throw ValidationException if already fulfilled")
        void completeAlreadyFulfilled_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale())); // fulfilled
            assertThrows(ValidationException.class, () -> service.completeSale(SALE_ID));
        }

        @Test
        @DisplayName("Should throw ValidationException if sale is cancelled")
        void completeCancelledSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(cancelledSale()));
            assertThrows(ValidationException.class, () -> service.completeSale(SALE_ID));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // changeSalePaymentMethod
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changeSalePaymentMethod")
    class ChangePaymentMethod {

        @Test
        @DisplayName("Should update payment method on a valid sale")
        void changePaymentMethod_success() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale())); // has pm id=1
            when(salesRepository.paymentMethodExists(2L)).thenReturn(true);

            service.changeSalePaymentMethod(SALE_ID, 2L);

            verify(salesRepository).updateSalePaymentMethod(eq(SALE_ID), eq(2L));
            verify(auditService).logUpdate(anyString(), eq(SALE_ID), any(), any(), anyString());
        }

        @Test
        @DisplayName("Should be a no-op if payment method is unchanged")
        void changePaymentMethod_sameMethod_noOp() {
            // paidSale() has paymentMethodId = 1L — setting same value should be a no-op
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.paymentMethodExists(1L)).thenReturn(true);

            service.changeSalePaymentMethod(SALE_ID, 1L);

            verify(salesRepository, never()).updateSalePaymentMethod(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw NotFoundException for non-existent payment method")
        void changePaymentMethod_invalidMethod_throwsNotFound() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.paymentMethodExists(99L)).thenReturn(false);

            assertThrows(NotFoundException.class, () -> service.changeSalePaymentMethod(SALE_ID, 99L));
        }

        @Test
        @DisplayName("Should throw ValidationException for cancelled sale")
        void changePaymentMethod_cancelledSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(cancelledSale()));
            assertThrows(ValidationException.class, () -> service.changeSalePaymentMethod(SALE_ID, 2L));
        }

        @Test
        @DisplayName("Should throw ValidationException for refunded sale")
        void changePaymentMethod_refundedSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(refundedSale()));
            assertThrows(ValidationException.class, () -> service.changeSalePaymentMethod(SALE_ID, 2L));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // changeSaleCustomer
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("changeSaleCustomer")
    class ChangeSaleCustomer {

        @Test
        @DisplayName("Should reassign customer on a valid sale")
        void changeCustomer_success() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));

            service.changeSaleCustomer(SALE_ID, 42L);

            verify(salesRepository).updateSaleCustomer(eq(SALE_ID), eq(42L));
            verify(auditService).logUpdate(anyString(), eq(SALE_ID), any(), any(), anyString());
        }

        @Test
        @DisplayName("Should allow removing customer (setting to null / walk-in)")
        void changeCustomer_toNull_success() {
            // paidSale has customerId = null already. We need a sale WITH a customer to test the remove path.
            Sale saleWithCustomer = new Sale(SALE_ID, "INV-001", null, new BigDecimal("100.00"), new BigDecimal("100.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled",
                    5L, "John", null, null, "System", 1L, "Cash", "INV-001");
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(saleWithCustomer));

            service.changeSaleCustomer(SALE_ID, null);

            verify(salesRepository).updateSaleCustomer(eq(SALE_ID), isNull());
        }

        @Test
        @DisplayName("Should be a no-op if customer is already the same (both null)")
        void changeCustomer_sameCustomer_noOp() {
            // paidSale has customerId = null; setting null again should be a no-op
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));

            service.changeSaleCustomer(SALE_ID, null);

            verify(salesRepository, never()).updateSaleCustomer(SALE_ID, null);
        }

        @Test
        @DisplayName("Should throw ValidationException for cancelled sale")
        void changeCustomer_cancelledSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(cancelledSale()));
            assertThrows(ValidationException.class, () -> service.changeSaleCustomer(SALE_ID, 1L));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // updateSaleItems
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateSaleItems")
    class UpdateSaleItems {

        @Test
        @DisplayName("Should re-calculate totals and re-insert items")
        void updateItems_recalculatesTotals() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(saleItem(2)));
            when(productRepository.findProductById(PRODUCT_ID)).thenReturn(Optional.of(product()));
            when(salesRepository.insertSaleItem(any())).thenReturn(99L);

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 3, new BigDecimal("50.00"), BigDecimal.ZERO)
            );

            service.updateSaleItems(SALE_ID, newItems);

            // Old items deleted, new item inserted
            verify(salesRepository).deleteSaleItem(1L);
            verify(salesRepository).insertSaleItem(any(SaleItem.class));

            // Total updated: 3 * 50 = 150 (no tax on product)
            ArgumentCaptor<BigDecimal> totalCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(salesRepository).updateSaleTotals(eq(SALE_ID), totalCaptor.capture(), any(), any());
            assertEquals(0, new BigDecimal("150.00").compareTo(totalCaptor.getValue()));
        }

        @Test
        @DisplayName("Should deduct stock when quantity increases")
        void updateItems_quantityIncrease_deductsStock() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(saleItem(1))); // was 1
            when(productRepository.findProductById(PRODUCT_ID)).thenReturn(Optional.of(product()));
            when(salesRepository.insertSaleItem(any())).thenReturn(99L);

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 3, new BigDecimal("50.00"), BigDecimal.ZERO) // now 3
            );

            service.updateSaleItems(SALE_ID, newItems);

            // Difference = 3 - 1 = 2 extra units deducted
            verify(inventoryService).deductStock(eq(PRODUCT_ID), eq(2), any(), any(), eq(SALE_ID));
        }

        @Test
        @DisplayName("Should restore stock when quantity decreases")
        void updateItems_quantityDecrease_restoresStock() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(saleItem(5))); // was 5
            when(productRepository.findProductById(PRODUCT_ID)).thenReturn(Optional.of(product()));
            when(salesRepository.insertSaleItem(any())).thenReturn(99L);

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 2, new BigDecimal("50.00"), BigDecimal.ZERO) // now 2
            );

            service.updateSaleItems(SALE_ID, newItems);

            // Difference = 2 - 5 = -3, so 3 units restored
            verify(inventoryService).adjustInventory(eq(PRODUCT_ID), eq(3), any(), anyString(), eq(SALE_ID), anyString());
        }

        @Test
        @DisplayName("Should throw ValidationException for cancelled sale")
        void updateItems_cancelledSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(cancelledSale()));

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 1, new BigDecimal("50.00"), BigDecimal.ZERO)
            );

            assertThrows(ValidationException.class, () -> service.updateSaleItems(SALE_ID, newItems));
        }

        @Test
        @DisplayName("Should throw ValidationException for refunded sale")
        void updateItems_refundedSale_throwsValidation() {
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(refundedSale()));

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 1, new BigDecimal("50.00"), BigDecimal.ZERO)
            );

            assertThrows(ValidationException.class, () -> service.updateSaleItems(SALE_ID, newItems));
        }

        @Test
        @DisplayName("Should also update paid amount if sale was fully paid")
        void updateItems_fullyPaidSale_updatesPaidAmount() {
            // paidSale has total == paidAmount == 100.00
            when(salesRepository.findSaleById(SALE_ID)).thenReturn(Optional.of(paidSale()));
            when(salesRepository.findSaleItems(SALE_ID)).thenReturn(List.of(saleItem(2)));
            when(productRepository.findProductById(PRODUCT_ID)).thenReturn(Optional.of(product()));
            when(salesRepository.insertSaleItem(any())).thenReturn(99L);

            List<UpdateSaleItemRequest> newItems = List.of(
                new UpdateSaleItemRequest(PRODUCT_ID, 1, new BigDecimal("80.00"), BigDecimal.ZERO)
            );

            service.updateSaleItems(SALE_ID, newItems);

            // New total = 80; since sale was fully paid, paidAmount should also be updated
            verify(salesRepository).updateSalePaidAmount(eq(SALE_ID), any(BigDecimal.class));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // upsertLegacySale (via SalesService validation layer)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Legacy sale validation")
    class LegacySaleValidation {

        private SalesService buildSalesService() {
            return new SalesService(salesRepository, productRepository, customerRepository,
                auditService, inventoryService, new com.picopossum.domain.services.SaleCalculator(),
                mock(PaymentService.class), transactionManager, jsonService, settingsStore,
                mock(InvoiceNumberService.class), mock(ReturnsRepository.class));
        }

        @Test
        @DisplayName("Should reject null legacy sale")
        void upsertLegacySale_null_throwsValidation() {
            SalesService ss = buildSalesService();
            assertThrows(ValidationException.class, () -> ss.upsertLegacySale(null));
        }

        @Test
        @DisplayName("Should reject legacy sale with blank invoice number")
        void upsertLegacySale_blankInvoice_throwsValidation() {
            SalesService ss = buildSalesService();
            LegacySale ls = new LegacySale("  ", java.time.LocalDateTime.now(), null, null, new BigDecimal("100.00"), 1L, "Cash", "f.csv");
            assertThrows(ValidationException.class, () -> ss.upsertLegacySale(ls));
        }

        @Test
        @DisplayName("Should reject legacy sale with null date")
        void upsertLegacySale_nullDate_throwsValidation() {
            SalesService ss = buildSalesService();
            LegacySale ls = new LegacySale("INV-001", null, null, null, new BigDecimal("100.00"), 1L, "Cash", "f.csv");
            assertThrows(ValidationException.class, () -> ss.upsertLegacySale(ls));
        }

        @Test
        @DisplayName("Should reject legacy sale with negative amount")
        void upsertLegacySale_negativeAmount_throwsValidation() {
            SalesService ss = buildSalesService();
            LegacySale ls = new LegacySale("INV-001", java.time.LocalDateTime.now(), null, null, new BigDecimal("-1.00"), 1L, "Cash", "f.csv");
            assertThrows(ValidationException.class, () -> ss.upsertLegacySale(ls));
        }

        @Test
        @DisplayName("Should allow legacy sale with zero amount")
        void upsertLegacySale_zeroAmount_success() {
            SalesService ss = buildSalesService();
            LegacySale ls = new LegacySale("INV-001", java.time.LocalDateTime.now(), null, null, BigDecimal.ZERO, 1L, "Cash", "f.csv");
            when(salesRepository.upsertLegacySale(any())).thenReturn(true);
            assertTrue(ss.upsertLegacySale(ls));
        }
    }
}

package com.possum.application.returns;

import com.possum.domain.services.ReturnCalculator;

import com.possum.application.returns.dto.CreateReturnItemRequest;
import com.possum.application.returns.dto.RefundCalculation;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.SaleItem;
import com.possum.testutil.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReturnCalculatorTest {
    private final ReturnCalculator calculator = new ReturnCalculator();

    private static CreateReturnItemRequest returnItem(long saleItemId, int qty) {
        return new CreateReturnItemRequest(saleItemId, qty);
    }

    // --- calculateRefunds ---

    @Test
    void calculateRefunds_noDiscount_fullQuantity_refundsFullLineTotal() {
        SaleItem item = Fixtures.saleItem(1L, 10L, 1L, 2, "50.00");
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 2)),
                List.of(item),
                BigDecimal.ZERO
        );
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("100.00"), result.get(0).refundAmount());
    }

    @Test
    void calculateRefunds_noDiscount_partialQuantity_proRatesCorrectly() {
        SaleItem item = Fixtures.saleItem(1L, 10L, 1L, 4, "25.00");
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 1)),
                List.of(item),
                BigDecimal.ZERO
        );
        // 4 * 25 = 100, returning 1 → 25.00
        assertEquals(new BigDecimal("25.00"), result.get(0).refundAmount());
    }

    @Test
    void calculateRefunds_withGlobalDiscount_proRatesDiscountAcrossLines() {
        SaleItem item1 = Fixtures.saleItem(1L, 10L, 1L, 1, "100.00");
        SaleItem item2 = Fixtures.saleItem(2L, 10L, 2L, 1, "100.00");
        // global discount = 20, each line gets 10 off → net 90 each
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 1)),
                List.of(item1, item2),
                new BigDecimal("20.00")
        );
        assertEquals(new BigDecimal("90.00"), result.get(0).refundAmount());
    }

    @Test
    void calculateRefunds_withLineDiscount_subtractedBeforeGlobalProration() {
        SaleItem item = Fixtures.saleItemWithDiscount(1L, 10L, 1L, 2, "60.00", "20.00");
        // line subtotal = 120 - 20 = 100, no global discount, returning 1 → 50
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 1)),
                List.of(item),
                BigDecimal.ZERO
        );
        assertEquals(new BigDecimal("50.00"), result.get(0).refundAmount());
    }

    @Test
    void calculateRefunds_unknownSaleItemId_throwsValidationException() {
        SaleItem item = Fixtures.saleItem(1L, 10L, 1L, 1, "100.00");
        assertThrows(ValidationException.class, () ->
                calculator.calculateRefunds(
                        List.of(returnItem(99L, 1)),
                        List.of(item),
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void calculateRefunds_zeroSubtotal_refundIsZero() {
        SaleItem item = Fixtures.saleItem(1L, 10L, 1L, 1, "0.00");
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 1)),
                List.of(item),
                BigDecimal.ZERO
        );
        assertEquals(new BigDecimal("0.00"), result.get(0).refundAmount());
    }

    @Test
    void calculateRefunds_setsCorrectVariantId() {
        SaleItem item = Fixtures.saleItem(1L, 10L, 42L, 1, "100.00");
        List<RefundCalculation> result = calculator.calculateRefunds(
                List.of(returnItem(1L, 1)),
                List.of(item),
                BigDecimal.ZERO
        );
        assertEquals(42L, result.get(0).variantId());
    }

    // --- calculateTotalRefund ---

    @Test
    void calculateTotalRefund_sumsAllRefundAmounts() {
        List<RefundCalculation> calcs = List.of(
                new RefundCalculation(1L, 1, new BigDecimal("30.00"), 1L),
                new RefundCalculation(2L, 1, new BigDecimal("70.00"), 2L)
        );
        assertEquals(new BigDecimal("100.00"), calculator.calculateTotalRefund(calcs));
    }

    @Test
    void calculateTotalRefund_emptyList_returnsZero() {
        assertEquals(BigDecimal.ZERO, calculator.calculateTotalRefund(List.of()));
    }
}

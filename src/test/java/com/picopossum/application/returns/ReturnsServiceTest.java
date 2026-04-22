package com.picopossum.application.returns;

import com.picopossum.application.returns.dto.CreateReturnItemRequest;
import com.picopossum.application.returns.dto.RefundCalculation;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.domain.services.ReturnCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnsServiceTest {

    @Test
    @DisplayName("SCENARIO 1: Full Return")
    void testFullReturn() {
        SaleItem saleItem = new SaleItem(
                1001L, 1L, 101L,
                "SKU-A", "Product A", 
                5, 
                new BigDecimal("100.00"), 
                new BigDecimal("50.00"),  
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        CreateReturnItemRequest returnItem = new CreateReturnItemRequest(1001L, 5);
        List<CreateReturnItemRequest> returnItems = List.of(returnItem);
        
        ReturnCalculator returnCalculator = new ReturnCalculator();
        List<RefundCalculation> refunds = returnCalculator.calculateRefunds(
                returnItems, saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = returnCalculator.calculateTotalRefund(refunds);
        
        assertEquals(1, refunds.size());
        assertEquals(5, refunds.get(0).quantity());
        assertEquals(0, refunds.get(0).refundAmount().compareTo(new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("SCENARIO 2: Partial Return")
    void testPartialReturn() {
        SaleItem saleItem = new SaleItem(
                2001L, 2L, 201L,
                "SKU-B", "Product B",
                10,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        CreateReturnItemRequest returnItem = new CreateReturnItemRequest(2001L, 3);
        
        ReturnCalculator calculator = new ReturnCalculator();
        List<RefundCalculation> refunds = calculator.calculateRefunds(
                List.of(returnItem), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = calculator.calculateTotalRefund(refunds);
        
        assertEquals(3, refunds.get(0).quantity());
        assertEquals(0, refunds.get(0).refundAmount().compareTo(new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("SCENARIO 5: Return with Line Discount")
    void testReturnWithLineDiscount() {
        SaleItem saleItem = new SaleItem(
                5001L, 5L, 501L,
                "SKU-E", "Product E", 
                10,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        ReturnCalculator calculator = new ReturnCalculator();
        List<RefundCalculation> refunds = calculator.calculateRefunds(
                List.of(new CreateReturnItemRequest(5001L, 3)), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = calculator.calculateTotalRefund(refunds);
        
        // (1000 - 100) / 10 * 3 = 270
        assertEquals(0, totalRefund.compareTo(new BigDecimal("270.00")));
    }
}

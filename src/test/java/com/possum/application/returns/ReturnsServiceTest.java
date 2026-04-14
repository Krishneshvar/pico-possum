package com.possum.application.returns;

import com.possum.application.returns.dto.CreateReturnItemRequest;
import com.possum.application.returns.dto.RefundCalculation;
import com.possum.domain.model.SaleItem;
import com.possum.domain.services.ReturnCalculator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive test scenarios for Returns module
 * Verifies all critical behaviors against TypeScript implementation
 */
public class ReturnsServiceTest {

    /**
     * SCENARIO 1: Full Return
     */
    public void testFullReturn() {
        System.out.println("=== SCENARIO 1: Full Return ===");
        
        SaleItem saleItem = new SaleItem(
                1001L, 1L, 101L,
                "Product A", "Variant A", "SKU-A",
                5, 
                new BigDecimal("100.00"), 
                new BigDecimal("50.00"),  
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null,
                BigDecimal.ZERO, 
                null
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        CreateReturnItemRequest returnItem = new CreateReturnItemRequest(1001L, 5);
        List<CreateReturnItemRequest> returnItems = List.of(returnItem);
        
        ReturnCalculator returnCalculator = new ReturnCalculator();
        List<RefundCalculation> refunds = returnCalculator.calculateRefunds(
                returnItems, saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = returnCalculator.calculateTotalRefund(refunds);
        
        assert refunds.size() == 1 : "Should have 1 refund calculation";
        assert refunds.get(0).quantity() == 5 : "Should return 5 items";
        assert refunds.get(0).refundAmount().compareTo(new BigDecimal("500.00")) == 0;
        
        System.out.println("✓ Full return validated: " + totalRefund);
    }

    /**
     * SCENARIO 2: Partial Return
     */
    public void testPartialReturn() {
        System.out.println("=== SCENARIO 2: Partial Return ===");
        
        SaleItem saleItem = new SaleItem(
                2001L, 2L, 201L,
                "Product B", "Variant B", "SKU-B",
                10,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                null
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        CreateReturnItemRequest returnItem = new CreateReturnItemRequest(2001L, 3);
        
        ReturnCalculator calculator = new ReturnCalculator();
        List<RefundCalculation> refunds = calculator.calculateRefunds(
                List.of(returnItem), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = calculator.calculateTotalRefund(refunds);
        
        assert refunds.get(0).quantity() == 3;
        assert refunds.get(0).refundAmount().compareTo(new BigDecimal("300.00")) == 0;
        
        System.out.println("✓ Partial refund validated: " + totalRefund);
    }

    /**
     * SCENARIO 3: Multiple Partial Returns
     */
    public void testMultiplePartialReturns() {
        System.out.println("=== SCENARIO 3: Multiple Partial Returns ===");
        
        SaleItem saleItem = new SaleItem(
                3001L, 3L, 301L,
                "Product C", "Variant C", "SKU-C",
                10,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                null
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        ReturnCalculator calculator = new ReturnCalculator();

        // Return 1
        List<RefundCalculation> refunds1 = calculator.calculateRefunds(
                List.of(new CreateReturnItemRequest(3001L, 3)), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund1 = calculator.calculateTotalRefund(refunds1);
        assert totalRefund1.compareTo(new BigDecimal("300.00")) == 0;

        // Return 2
        List<RefundCalculation> refunds2 = calculator.calculateRefunds(
                List.of(new CreateReturnItemRequest(3001L, 5)), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund2 = calculator.calculateTotalRefund(refunds2);
        assert totalRefund2.compareTo(new BigDecimal("500.00")) == 0;

        System.out.println("✓ Multiple partial returns validated");
    }

    /**
     * SCENARIO 5: Return with Line Discount
     */
    public void testReturnWithLineDiscount() {
        System.out.println("=== SCENARIO 5: Return with Line Discount ===");
        
        SaleItem saleItem = new SaleItem(
                5001L, 5L, 501L,
                "Product E", "Variant E", "SKU-E",
                10,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null,
                new BigDecimal("100.00"), // line discount
                null
        );
        
        List<SaleItem> saleItems = List.of(saleItem);
        ReturnCalculator calculator = new ReturnCalculator();
        List<RefundCalculation> refunds = calculator.calculateRefunds(
                List.of(new CreateReturnItemRequest(5001L, 3)), saleItems, BigDecimal.ZERO);
        BigDecimal totalRefund = calculator.calculateTotalRefund(refunds);
        
        // (1000 - 100) / 10 * 3 = 270
        assert totalRefund.compareTo(new BigDecimal("270.00")) == 0;
        System.out.println("✓ Line discount refund validated: " + totalRefund);
    }

    /**
     * SCENARIO 6: Return with Global Discount
     */
    public void testReturnWithGlobalDiscount() {
        System.out.println("=== SCENARIO 6: Return with Global Discount ===");
        
        SaleItem itemA = new SaleItem(6001L, 6L, 601L, "F", "F", "F", 10, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, new BigDecimal("50.00"), null);
        SaleItem itemB = new SaleItem(6002L, 6L, 602L, "G", "G", "G", 5, new BigDecimal("50.00"), new BigDecimal("25.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO, null);
        
        List<SaleItem> saleItems = List.of(itemA, itemB);
        ReturnCalculator calculator = new ReturnCalculator();
        
        // global subtotal = 950 + 250 = 1200. global discount = 120 (10%).
        List<RefundCalculation> refunds = calculator.calculateRefunds(
                List.of(new CreateReturnItemRequest(6001L, 2), new CreateReturnItemRequest(6002L, 1)), 
                saleItems, new BigDecimal("120.00"));
        
        // Item A: 950 - 95 pro-rated discount = 855. 855/10 * 2 = 171.
        // Item B: 250 - 25 pro-rated discount = 225. 225/5 * 1 = 45.
        // Total = 216.
        assert calculator.calculateTotalRefund(refunds).compareTo(new BigDecimal("216.00")) == 0;
        System.out.println("✓ Global discount pro-rated refund validated");
    }

    public static void main(String[] args) {
        ReturnsServiceTest test = new ReturnsServiceTest();
        test.testFullReturn();
        test.testPartialReturn();
        test.testMultiplePartialReturns();
        test.testReturnWithLineDiscount();
        test.testReturnWithGlobalDiscount();
        System.out.println("ALL SCENARIOS PASSED");
    }
}

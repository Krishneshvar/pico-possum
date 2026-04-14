package com.possum.application.sales;

import com.possum.application.sales.dto.CreateSaleItemRequest;
import com.possum.application.sales.dto.CreateSaleRequest;
import com.possum.application.sales.dto.PaymentRequest;
import com.possum.application.sales.dto.SaleResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Example usage of SalesService
 * 
 * This class demonstrates how to use the sales module services.
 * In a real application, these would be called from controllers or UI handlers.
 */
public class SalesServiceExample {
    
    private final SalesService salesService;
    
    public SalesServiceExample(SalesService salesService) {
        this.salesService = salesService;
    }
    
    /**
     * Example: Create a simple sale with one item and full payment
     */
    public SaleResponse createSimpleSale(long variantId, int quantity, long paymentMethodId, long userId) {
        CreateSaleItemRequest item = new CreateSaleItemRequest(
                variantId,
                quantity,
                null,  // No line discount
                null   // Use variant's default price
        );
        
        // Calculate expected total (would need to fetch variant price in real scenario)
        BigDecimal expectedTotal = BigDecimal.valueOf(100.00);
        
        PaymentRequest payment = new PaymentRequest(expectedTotal, paymentMethodId);
        
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(item),
                null,  // No customer
                null,  // No global discount
                List.of(payment)
        );
        
        return salesService.createSale(request, userId);
    }
    
    /**
     * Example: Create a sale with multiple items, discount, and partial payment
     */
    public SaleResponse createComplexSale(long customerId, long userId) {
        CreateSaleItemRequest item1 = new CreateSaleItemRequest(
                1L,    // variantId
                2,     // quantity
                BigDecimal.valueOf(5.00),  // Line discount
                null   // Use default price
        );
        
        CreateSaleItemRequest item2 = new CreateSaleItemRequest(
                2L,    // variantId
                1,     // quantity
                null,  // No line discount
                BigDecimal.valueOf(50.00)  // Override price
        );
        
        PaymentRequest payment = new PaymentRequest(
                BigDecimal.valueOf(50.00),  // Partial payment
                1L  // paymentMethodId
        );
        
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(item1, item2),
                customerId,
                BigDecimal.valueOf(10.00),  // Global discount
                List.of(payment)
        );
        
        return salesService.createSale(request, userId);
    }
    
    /**
     * Example: Create a draft sale (no payment)
     */
    public SaleResponse createDraftSale(long variantId, int quantity, long userId) {
        CreateSaleItemRequest item = new CreateSaleItemRequest(
                variantId,
                quantity,
                null,
                null
        );
        
        CreateSaleRequest request = new CreateSaleRequest(
                List.of(item),
                null,
                null,
                List.of()  // No payments - creates draft
        );
        
        return salesService.createSale(request, userId);
    }
    
    /**
     * Example: Get sale details
     */
    public SaleResponse getSaleDetails(long saleId) {
        return salesService.getSaleDetails(saleId);
    }
    
    /**
     * Example: Cancel a sale
     */
    public void cancelSale(long saleId, long userId) {
        salesService.cancelSale(saleId, userId);
    }
    
    /**
     * Example: Complete/fulfill a sale
     */
    public void completeSale(long saleId, long userId) {
        salesService.completeSale(saleId, userId);
    }
}

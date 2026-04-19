package com.picopossum.application.sales;

import com.picopossum.application.sales.dto.CreateSaleItemRequest;
import com.picopossum.application.sales.dto.CreateSaleRequest;
import com.picopossum.application.sales.dto.PaymentRequest;
import com.picopossum.application.sales.dto.SaleResponse;

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
    public SaleResponse createSimpleSale(long productId, int quantity, long paymentMethodId, long userId) {
        CreateSaleItemRequest item = new CreateSaleItemRequest(
                productId,
                quantity,
                null,  // No line discount
                null   // Use product's default price
        );
        
        // Calculate expected total (would need to fetch product price in real scenario)
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
     * Example: Create a draft sale (no payment)
     */
    public SaleResponse createDraftSale(long productId, int quantity, long userId) {
        CreateSaleItemRequest item = new CreateSaleItemRequest(
                productId,
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
}

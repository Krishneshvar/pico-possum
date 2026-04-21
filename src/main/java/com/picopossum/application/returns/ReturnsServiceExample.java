package com.picopossum.application.returns;

import com.picopossum.application.returns.dto.CreateReturnItemRequest;
import com.picopossum.application.returns.dto.CreateReturnRequest;
import com.picopossum.application.returns.dto.ReturnResponse;
import com.picopossum.domain.model.Return;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;

import java.util.List;

/**
 * Usage examples for ReturnsService (Modernized for Single-User SMB)
 * 
 * Demonstrates the complete return lifecycle:
 * 1. Create return with quantity validation
 * 2. Calculate pro-rated refunds
 * 3. Restore inventory (Direct stock movement)
 * 4. Create refund transaction
 * 5. Update sale status
 */
public class ReturnsServiceExample {

    private final ReturnsService returnsService;

    public ReturnsServiceExample(ReturnsService returnsService) {
        this.returnsService = returnsService;
    }

    /**
     * Example 1: Simple return - single item, partial quantity
     */
    public void simpleReturn() {
        // Return 2 units of item from sale
        CreateReturnRequest request = new CreateReturnRequest(
                1001L,  // saleId
                List.of(new CreateReturnItemRequest(5001L, 2)),  // saleItemId, quantity
                "Customer changed mind"
        );

        ReturnResponse response = returnsService.createReturn(request);
        System.out.println("Return created: " + response.id());
        System.out.println("Total refund: " + response.totalRefund());
    }

    /**
     * Example 2: Multiple items return
     */
    public void multipleItemsReturn() {
        CreateReturnRequest request = new CreateReturnRequest(
                1002L,
                List.of(
                        new CreateReturnItemRequest(5010L, 1),
                        new CreateReturnItemRequest(5011L, 3)
                ),
                "Defective products"
        );

        ReturnResponse response = returnsService.createReturn(request);
        System.out.println("Returned " + response.itemCount() + " items");
    }

    /**
     * Example 3: Get return details
     */
    public void getReturnDetails() {
        Return returnRecord = returnsService.getReturn(1L);
        System.out.println("Return for sale: " + returnRecord.saleId());
        System.out.println("Processed by: " + returnRecord.processedByName());
        System.out.println("Total refund: " + returnRecord.totalRefund());
    }

    /**
     * Example 4: Get all returns for a sale
     */
    public void getSaleReturns() {
        List<Return> returns = returnsService.getSaleReturns(1001L);
        System.out.println("Found " + returns.size() + " returns for sale");
    }

    /**
     * Example 5: Search returns with filters
     */
    public void searchReturns() {
        ReturnFilter filter = new ReturnFilter(
                null,  // saleId
                null,  // startDate
                null,  // endDate
                null,  // minAmount
                null,  // maxAmount
                null,  // paymentMethodIds
                null,  // searchTerm
                1,     // currentPage
                20,    // itemsPerPage
                "created_at",  // sortBy
                "DESC"  // sortOrder
        );

        PagedResult<Return> result = returnsService.getReturns(filter);
        System.out.println("Total returns: " + result.totalCount());
        System.out.println("Page " + result.page() + " of " + result.totalPages());
    }

    /**
     * Example 6: Validation - quantity exceeds available
     * This will throw ValidationException
     */
    public void invalidQuantityReturn() {
        CreateReturnRequest request = new CreateReturnRequest(
                1003L,
                List.of(new CreateReturnItemRequest(5020L, 3)),
                "Test"
        );

        try {
            returnsService.createReturn(request);
        } catch (Exception e) {
            System.out.println("Expected error: " + e.getMessage());
        }
    }

    /**
     * Example 7: Validation - refund exceeds paid amount
     */
    public void invalidRefundAmount() {
        CreateReturnRequest request = new CreateReturnRequest(
                1004L,
                List.of(new CreateReturnItemRequest(5030L, 10)),
                "Test"
        );

        try {
            returnsService.createReturn(request);
        } catch (Exception e) {
            System.out.println("Expected error: " + e.getMessage());
        }
    }
}

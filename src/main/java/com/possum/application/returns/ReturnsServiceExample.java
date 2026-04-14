package com.possum.application.returns;

import com.possum.application.returns.dto.CreateReturnItemRequest;
import com.possum.application.returns.dto.CreateReturnRequest;
import com.possum.application.returns.dto.ReturnResponse;
import com.possum.domain.model.Return;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;

import java.util.List;

/**
 * Usage examples for ReturnsService
 * 
 * Demonstrates the complete return lifecycle:
 * 1. Create return with quantity validation
 * 2. Calculate pro-rated refunds
 * 3. Restore inventory (FIFO reversal)
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
                "Customer changed mind",
                100L  // userId
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
                "Defective products",
                100L
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
                100L,  // userId - returns processed by this user
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
        // Assuming sale item has quantity=5 and 3 already returned
        // Trying to return 3 more (total would be 6 > 5)
        CreateReturnRequest request = new CreateReturnRequest(
                1003L,
                List.of(new CreateReturnItemRequest(5020L, 3)),
                "Test",
                100L
        );

        try {
            returnsService.createReturn(request);
        } catch (Exception e) {
            System.out.println("Expected error: " + e.getMessage());
            // "Cannot return 3 of Product X. Only 2 remaining to return."
        }
    }

    /**
     * Example 7: Validation - refund exceeds paid amount
     * This will throw ValidationException
     */
    public void invalidRefundAmount() {
        // Assuming sale paid_amount=100 but refund would be 150
        CreateReturnRequest request = new CreateReturnRequest(
                1004L,
                List.of(new CreateReturnItemRequest(5030L, 10)),
                "Test",
                100L
        );

        try {
            returnsService.createReturn(request);
        } catch (Exception e) {
            System.out.println("Expected error: " + e.getMessage());
            // "Cannot refund 150.00. Maximum refundable amount is 100.00."
        }
    }

    /**
     * Key behaviors preserved from TypeScript:
     * 
     * 1. Partial returns are allowed
     *    - Can return subset of items
     *    - Can return partial quantities
     * 
     * 2. Cumulative quantity validation
     *    - Total returned quantity cannot exceed sold quantity
     *    - Tracks all previous returns for same sale item
     * 
     * 3. Refund amount validation
     *    - Refund cannot exceed sale paid amount
     *    - Pro-rated discount distribution (line + global)
     * 
     * 4. Inventory restoration
     *    - Reverses exact FIFO lots from original sale
     *    - Uses LIFO order (newest deduction first)
     *    - Handles headless stock gracefully
     * 
     * 5. Transaction integrity
     *    - All operations in single transaction
     *    - Rollback on any validation failure
     *    - Audit trail for all changes
     * 
     * 6. Sale status updates
     *    - Status becomes 'refunded' when paid_amount <= 0
     *    - Partial refunds don't change status
     */
}

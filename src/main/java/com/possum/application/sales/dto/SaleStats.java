package com.possum.application.sales.dto;

public record SaleStats(
        long totalBills,
        long paidCount,
        long partialOrDraftCount,
        long cancelledOrRefundedCount
) {
}

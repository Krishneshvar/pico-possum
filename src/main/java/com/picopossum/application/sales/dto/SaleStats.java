package com.picopossum.application.sales.dto;

public record SaleStats(
        long totalBills,
        long paidCount,
        long partialOrDraftCount,
        long cancelledOrRefundedCount
) {
}

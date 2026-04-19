package com.picopossum.application.returns.dto;

public record CreateReturnItemRequest(
        Long saleItemId,
        Integer quantity
) {
}

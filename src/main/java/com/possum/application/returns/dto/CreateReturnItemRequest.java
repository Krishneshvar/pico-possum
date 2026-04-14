package com.possum.application.returns.dto;

public record CreateReturnItemRequest(
        Long saleItemId,
        Integer quantity
) {
}

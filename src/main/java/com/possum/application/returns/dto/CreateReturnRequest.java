package com.possum.application.returns.dto;

import java.util.List;

public record CreateReturnRequest(
        Long saleId,
        List<CreateReturnItemRequest> items,
        String reason,
        Long userId
) {
}

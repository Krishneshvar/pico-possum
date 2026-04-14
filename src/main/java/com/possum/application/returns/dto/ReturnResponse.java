package com.possum.application.returns.dto;

import java.math.BigDecimal;

public record ReturnResponse(
        Long id,
        Long saleId,
        BigDecimal totalRefund,
        Integer itemCount
) {
}

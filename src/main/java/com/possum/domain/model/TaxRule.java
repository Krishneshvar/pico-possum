package com.possum.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaxRule(
        Long id,
        Long taxProfileId,
        Long taxCategoryId,
        String ruleScope,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minInvoiceTotal,
        BigDecimal maxInvoiceTotal,
        String customerType,
        BigDecimal ratePercent,
        Boolean compound,
        Integer priority,
        LocalDate validFrom,
        LocalDate validTo,
        String categoryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.possum.application.reports.dto;

import com.possum.domain.model.ProductFlow;

import java.util.List;
import java.util.Map;

public record ProductFlowReport(
        long variantId,
        Map<String, Object> summary,
        List<ProductFlow> flows
) {
}

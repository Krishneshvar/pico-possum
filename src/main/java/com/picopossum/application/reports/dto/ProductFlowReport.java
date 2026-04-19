package com.picopossum.application.reports.dto;

import com.picopossum.domain.model.ProductFlow;

import java.util.List;
import java.util.Map;

public record ProductFlowReport(
        long productId,
        Map<String, Object> summary,
        List<ProductFlow> flows
) {
}

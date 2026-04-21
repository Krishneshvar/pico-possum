package com.picopossum.application.inventory;

import com.picopossum.domain.enums.FlowEventType;
import com.picopossum.domain.model.ProductFlow;
import com.picopossum.domain.repositories.ProductFlowRepository;

import com.picopossum.shared.util.TimeUtil;
import java.util.List;
import java.util.Map;

public class ProductFlowService {
    private final ProductFlowRepository productFlowRepository;

    public ProductFlowService(ProductFlowRepository productFlowRepository) {
        this.productFlowRepository = productFlowRepository;
    }

    public com.picopossum.application.reports.dto.ProductFlowReport getProductFlowReport(long productId, int limit, int offset, String startDate, String endDate, List<String> eventTypes) {
        Map<String, Object> summary = productFlowRepository.getProductFlowSummary(productId);
        List<ProductFlow> flows = productFlowRepository.findFlowByProductId(productId, limit, offset, startDate, endDate, eventTypes);
        return new com.picopossum.application.reports.dto.ProductFlowReport(productId, summary, flows);
    }


    public List<ProductFlow> getFlowByReference(String referenceType, long referenceId) {
        return productFlowRepository.findFlowByReference(referenceType, referenceId);
    }

    public void logProductFlow(long productId, FlowEventType eventType, int quantity, String referenceType, Long referenceId) {
        ProductFlow flow = new ProductFlow(
                null,
                productId,
                eventType.getValue(),
                quantity,
                referenceType,
                referenceId,
                null,
                null,
                null,
                null,
                null,
                TimeUtil.nowUTC()
        );
        productFlowRepository.insertProductFlow(flow);
    }
}

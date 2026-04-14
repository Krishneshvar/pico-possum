package com.possum.application.inventory;

import com.possum.domain.enums.FlowEventType;
import com.possum.domain.model.ProductFlow;
import com.possum.domain.repositories.ProductFlowRepository;

import com.possum.shared.util.TimeUtil;
import java.util.List;
import java.util.Map;

public class ProductFlowService {
    private final ProductFlowRepository productFlowRepository;

    public ProductFlowService(ProductFlowRepository productFlowRepository) {
        this.productFlowRepository = productFlowRepository;
    }

    public List<ProductFlow> getProductTimeline(long productId, int limit, int offset, String startDate, String endDate, List<String> eventTypes) {
        return productFlowRepository.findFlowByProductId(productId, limit, offset, startDate, endDate, eventTypes);
    }

    public Map<String, Object> getProductFlowSummary(long productId) {
        return productFlowRepository.getProductFlowSummary(productId);
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

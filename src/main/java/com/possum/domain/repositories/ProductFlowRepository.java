package com.possum.domain.repositories;

import com.possum.domain.model.ProductFlow;

import java.util.List;
import java.util.Map;

public interface ProductFlowRepository {
    long insertProductFlow(ProductFlow flow);

    List<ProductFlow> findFlowByVariantId(long variantId, int limit, int offset, String startDate, String endDate, List<String> eventTypes);
    Map<String, Object> getFlowSummary(long variantId);

    List<ProductFlow> findFlowByProductId(long productId, int limit, int offset, String startDate, String endDate, List<String> eventTypes);
    Map<String, Object> getProductFlowSummary(long productId);

    List<ProductFlow> findFlowByReference(String referenceType, long referenceId);
}

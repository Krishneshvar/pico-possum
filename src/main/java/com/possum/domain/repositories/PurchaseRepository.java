package com.possum.domain.repositories;

import com.possum.domain.model.PurchaseOrder;
import com.possum.domain.model.PurchaseOrderItem;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository {
    PagedResult<PurchaseOrder> getAllPurchaseOrders(PurchaseOrderFilter filter);

    Optional<PurchaseOrder> getPurchaseOrderById(long id);

    List<PurchaseOrderItem> getPurchaseOrderItems(long purchaseOrderId);

    long createPurchaseOrder(long supplierId, String invoiceNumber, long paymentMethodId, long createdBy, List<PurchaseOrderItem> items);

    boolean updatePurchaseOrder(long id, long supplierId, long paymentMethodId, List<PurchaseOrderItem> items);

    boolean receivePurchaseOrder(long purchaseOrderId, long userId);

    int cancelPurchaseOrder(long id);
}

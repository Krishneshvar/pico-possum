package com.possum.domain.repositories;

import com.possum.domain.model.Return;
import com.possum.domain.model.ReturnItem;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;

import java.util.List;
import java.util.Optional;

public interface ReturnsRepository {
    long insertReturn(Return returnRecord);

    long insertReturnItem(ReturnItem item);

    Optional<Return> findReturnById(long id);

    List<Return> findReturnsBySaleId(long saleId);

    List<ReturnItem> findReturnItems(long returnId);

    PagedResult<Return> findReturns(ReturnFilter filter);

    int getTotalReturnedQuantity(long saleItemId);
}

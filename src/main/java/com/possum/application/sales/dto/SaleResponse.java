package com.possum.application.sales.dto;

import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;

import java.util.List;

public record SaleResponse(
        Sale sale,
        List<SaleItem> items,
        List<Transaction> transactions
) {}

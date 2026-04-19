package com.picopossum.application.sales.dto;

import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.domain.model.Transaction;

import java.util.List;

public record SaleResponse(
        Sale sale,
        List<SaleItem> items,
        List<Transaction> transactions
) {}

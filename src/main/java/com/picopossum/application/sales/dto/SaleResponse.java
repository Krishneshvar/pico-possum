package com.picopossum.application.sales.dto;

import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;


import java.util.List;

public record SaleResponse(
        Sale sale,
        List<SaleItem> items,
        List<com.picopossum.domain.model.Return> returns
) {}

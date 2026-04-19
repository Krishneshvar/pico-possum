package com.picopossum.domain.repositories;

import com.picopossum.domain.model.PaymentMethod;
import com.picopossum.domain.model.LegacySale;
import com.picopossum.domain.model.Sale;
import com.picopossum.domain.model.SaleItem;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.SaleFilter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SalesRepository {
    long insertSale(Sale sale);

    long insertSaleItem(SaleItem item);

    Optional<Sale> findSaleById(long id);
    
    Optional<Sale> findSaleByInvoiceNumber(String invoiceNumber);

    List<SaleItem> findSaleItems(long saleId);

    PagedResult<Sale> findSales(SaleFilter filter);

    com.picopossum.application.sales.dto.SaleStats getSaleStats(SaleFilter filter);

    int updateSaleStatus(long id, String status);

    int updateFulfillmentStatus(long id, String status);

    int updateSalePaidAmount(long id, BigDecimal paidAmount);

    Optional<String> getLastSaleInvoiceNumber();

    List<PaymentMethod> findPaymentMethods();

    boolean paymentMethodExists(long id);

    boolean saleExists(long id);

    Optional<String> getPaymentMethodCode(long paymentMethodId);

    long getNextSequenceForPaymentType(String paymentTypeCode);

    int updateSaleCustomer(long saleId, Long customerId);
    
    int deleteSaleItem(long itemId);

    int updateSaleItem(SaleItem item);

    int updateSaleTotals(long saleId, BigDecimal totalAmount, BigDecimal discount);
    
    int updateSalePaymentMethod(long saleId, long paymentMethodId);

    boolean upsertLegacySale(LegacySale legacySale);
}

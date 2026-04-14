package com.possum.domain.repositories;

import com.possum.domain.model.PaymentMethod;
import com.possum.domain.model.LegacySale;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.domain.model.Transaction;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.SaleFilter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SalesRepository {
    long insertSale(Sale sale);

    long insertSaleItem(SaleItem item);

    Optional<Sale> findSaleById(long id);
    
    Optional<Sale> findSaleByInvoiceNumber(String invoiceNumber);

    List<SaleItem> findSaleItems(long saleId);

    List<Transaction> findTransactionsBySaleId(long saleId);

    PagedResult<Sale> findSales(SaleFilter filter);

    com.possum.application.sales.dto.SaleStats getSaleStats(SaleFilter filter);

    int updateSaleStatus(long id, String status);

    int updateFulfillmentStatus(long id, String status);

    int updateSalePaidAmount(long id, BigDecimal paidAmount);

    long insertTransaction(Transaction transaction, Long saleId);

    Optional<String> getLastSaleInvoiceNumber();

    List<PaymentMethod> findPaymentMethods();

    boolean paymentMethodExists(long id);

    boolean saleExists(long id);

    Optional<String> getPaymentMethodCode(long paymentMethodId);

    long getNextSequenceForPaymentType(String paymentTypeCode);

    int updateTransactionPaymentMethod(long saleId, long newPaymentMethodId);

    int updateSaleCustomer(long saleId, Long customerId);
    
    int deleteSaleItem(long itemId);

    int updateSaleItem(SaleItem item);

    int updateSaleTotals(long saleId, BigDecimal totalAmount, BigDecimal totalTax, BigDecimal discount);

    boolean upsertLegacySale(LegacySale legacySale);

}

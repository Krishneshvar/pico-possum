package com.possum.application.sales;

import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.dto.*;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.*;
import com.possum.domain.repositories.*;
import com.possum.domain.services.SaleCalculator;
import com.possum.domain.services.TaxCalculator;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class SalesService {
    private final SalesRepository salesRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;
    private final CheckoutService checkoutService;
    private final SalesModificationService modificationService;

    public SalesService(SalesRepository salesRepository,
                        VariantRepository variantRepository,
                        ProductRepository productRepository,
                        CustomerRepository customerRepository,
                        AuditRepository auditRepository,
                        InventoryService inventoryService,
                        TaxCalculator taxCalculator,
                        SaleCalculator saleCalculator,
                        PaymentService paymentService,
                        TransactionManager transactionManager,
                        JsonService jsonService,
                        SettingsStore settingsStore,
                        InvoiceNumberService invoiceNumberService) {
        this.salesRepository = salesRepository;
        this.customerRepository = customerRepository;
        this.paymentService = paymentService;

        this.checkoutService = new CheckoutService(
            salesRepository, variantRepository, customerRepository, auditRepository,
            inventoryService, saleCalculator, transactionManager, jsonService, settingsStore, invoiceNumberService
        );
        this.modificationService = new SalesModificationService(
            salesRepository, variantRepository, productRepository, customerRepository, auditRepository,
            inventoryService, taxCalculator, transactionManager, jsonService, settingsStore
        );
    }

    public SaleResponse createSale(CreateSaleRequest request, long userId) {
        return checkoutService.createSale(request, userId);
    }

    public SaleResponse getSaleDetails(long saleId) {
        Sale sale = salesRepository.findSaleById(saleId)
                .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));
        List<SaleItem> items = salesRepository.findSaleItems(saleId);
        List<Transaction> transactions = salesRepository.findTransactionsBySaleId(saleId);
        return new SaleResponse(sale, items, transactions);
    }

    public Optional<Sale> findSaleByInvoiceNumber(String invoiceNumber) {
        return salesRepository.findSaleByInvoiceNumber(invoiceNumber);
    }

    public void updateSaleItems(long saleId, List<UpdateSaleItemRequest> itemRequests, long userId) {
        modificationService.updateSaleItems(saleId, itemRequests, userId);
    }

    public void addItemToSale(long saleId, CreateSaleItemRequest item, long userId) {
        throw new UnsupportedOperationException("Use updateSaleItems for batch modifications.");
    }

    public void removeItemFromSale(long saleId, long saleItemId, long userId) {
        throw new UnsupportedOperationException("Use updateSaleItems for batch modifications.");
    }

    public void cancelSale(long saleId, long userId) {
        modificationService.cancelSale(saleId, userId);
    }

    public void completeSale(long saleId, long userId) {
        modificationService.completeSale(saleId, userId);
    }

    public List<PaymentMethod> getPaymentMethods() {
        return paymentService.getActivePaymentMethods();
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findCustomers(
            new com.possum.shared.dto.CustomerFilter(null, null, null, 1, 1000, "name", "asc")
        ).items();
    }

    public com.possum.shared.dto.PagedResult<Sale> findSales(com.possum.shared.dto.SaleFilter filter) {
        return salesRepository.findSales(filter);
    }

    public SaleStats getSaleStats(com.possum.shared.dto.SaleFilter filter) {
        return salesRepository.getSaleStats(filter);
    }

    public boolean upsertLegacySale(LegacySale legacySale) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
        if (legacySale == null) {
            throw new ValidationException("Legacy sale data is required");
        }
        if (legacySale.invoiceNumber() == null || legacySale.invoiceNumber().isBlank()) {
            throw new ValidationException("Invoice number is required for legacy sale");
        }
        if (legacySale.saleDate() == null) {
            throw new ValidationException("Sale date is required for legacy sale");
        }
        if (legacySale.netAmount() == null || legacySale.netAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Legacy net amount must be zero or greater");
        }
        return salesRepository.upsertLegacySale(legacySale);
    }

    public void changeSalePaymentMethod(long saleId, long newPaymentMethodId, long userId) {
        modificationService.changeSalePaymentMethod(saleId, newPaymentMethodId, userId);
    }

    public void changeSaleCustomer(long saleId, Long newCustomerId, long userId) {
        modificationService.changeSaleCustomer(saleId, newCustomerId, userId);
    }
}

package com.possum.application.sales;

import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.dto.*;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.logging.AuditLogger;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.possum.shared.util.TimeUtil;
import java.util.*;

public class EnhancedSalesService {
    private final SalesRepository salesRepository;
    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final EnhancedTaxEngine taxEngine;
    private final PaymentService paymentService;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;
    private final InvoiceNumberService invoiceNumberService;
    private final AuditLogger auditLogger;

    public EnhancedSalesService(
            SalesRepository salesRepository,
            VariantRepository variantRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            InventoryService inventoryService,
            EnhancedTaxEngine taxEngine,
            PaymentService paymentService,
            TransactionManager transactionManager,
            JsonService jsonService,
            SettingsStore settingsStore,
            InvoiceNumberService invoiceNumberService,
            AuditLogger auditLogger
    ) {
        this.salesRepository = salesRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
        this.taxEngine = taxEngine;
        this.paymentService = paymentService;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
        this.invoiceNumberService = invoiceNumberService;
        this.auditLogger = auditLogger;
    }

    public SaleResponse createSale(CreateSaleRequest request, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(
                com.possum.application.auth.Permissions.SALES_CREATE);
        request.validate();

        BigDecimal discount = request.discount() != null ? request.discount() : BigDecimal.ZERO;
        List<PaymentRequest> payments = request.payments() != null ? request.payments() : List.of();

        for (PaymentRequest payment : payments) {
            paymentService.validatePaymentMethod(payment.paymentMethodId());
        }

        List<Long> variantIds = request.items().stream().map(CreateSaleItemRequest::variantId).toList();
        Map<Long, Variant> variantMap = fetchVariantsBatch(variantIds);
        boolean enforceInventoryRestrictions = isInventoryRestrictionsEnabled();

        return transactionManager.runInTransaction(() -> {
            for (CreateSaleItemRequest item : request.items()) {
                Variant variant = variantMap.get(item.variantId());
                if (variant == null) {
                    throw new NotFoundException("Variant not found: " + item.variantId());
                }

                if (enforceInventoryRestrictions) {
                    int currentStock = inventoryService.getVariantStock(item.variantId());
                    if (currentStock < item.quantity()) {
                        throw new InsufficientStockException(currentStock, item.quantity());
                    }
                }
            }

            taxEngine.init();

            BigDecimal grossTotal = BigDecimal.ZERO;
            List<TempItem> tempItems = new ArrayList<>();

            for (CreateSaleItemRequest item : request.items()) {
                Variant variant = variantMap.get(item.variantId());
                BigDecimal pricePerUnit = item.pricePerUnit() != null ? item.pricePerUnit() : variant.price();
                BigDecimal lineTotal = pricePerUnit.multiply(BigDecimal.valueOf(item.quantity()));
                BigDecimal lineDiscount = item.discount() != null ? item.discount() : BigDecimal.ZERO;
                BigDecimal netLineTotal = lineTotal.subtract(lineDiscount).max(BigDecimal.ZERO);

                grossTotal = grossTotal.add(netLineTotal);
                tempItems.add(new TempItem(item, pricePerUnit, netLineTotal));
            }

            BigDecimal distributedGlobalDiscount = BigDecimal.ZERO;
            List<TaxableItem> calculationItems = new ArrayList<>();

            for (int i = 0; i < tempItems.size(); i++) {
                TempItem tempItem = tempItems.get(i);
                Variant variant = variantMap.get(tempItem.item.variantId());
                Product product = productRepository.findProductById(variant.productId())
                        .orElseThrow(() -> new NotFoundException("Product not found"));

                BigDecimal itemGlobalDiscount = BigDecimal.ZERO;
                if (grossTotal.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(BigDecimal.ZERO) > 0) {
                    if (i == tempItems.size() - 1) {
                        itemGlobalDiscount = discount.subtract(distributedGlobalDiscount);
                    } else {
                        itemGlobalDiscount = tempItem.netLineTotal
                                .divide(grossTotal, 10, RoundingMode.HALF_UP)
                                .multiply(discount);
                        distributedGlobalDiscount = distributedGlobalDiscount.add(itemGlobalDiscount);
                    }
                }

                BigDecimal finalTaxableAmount = tempItem.netLineTotal.subtract(itemGlobalDiscount).max(BigDecimal.ZERO);
                BigDecimal effectiveUnitPrice = tempItem.item.quantity() > 0
                        ? finalTaxableAmount.divide(BigDecimal.valueOf(tempItem.item.quantity()), 10, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                TaxableItem taxableItem = new TaxableItem(
                        product.name(),
                        variant.name(),
                        effectiveUnitPrice,
                        tempItem.item.quantity(),
                        product.taxCategoryId(),
                        variant.id(),
                        product.id()
                );
                calculationItems.add(taxableItem);
            }

            Customer customer = null;
            if (request.customerId() != null) {
                customer = customerRepository.findCustomerById(request.customerId()).orElse(null);
            }

            TaxCalculationResult taxResult = taxEngine.calculate(new TaxableInvoice(calculationItems), customer);

            BigDecimal totalTax = taxResult.totalTax();
            List<ProcessedItem> processedItems = new ArrayList<>();

            for (int i = 0; i < request.items().size(); i++) {
                CreateSaleItemRequest item = request.items().get(i);
                TaxableItem calculatedItem = taxResult.getItemByIndex(i);
                Variant variant = variantMap.get(item.variantId());
                Product product = productRepository.findProductById(variant.productId())
                        .orElseThrow(() -> new NotFoundException("Product not found"));

                BigDecimal pricePerUnit = item.pricePerUnit() != null ? item.pricePerUnit() : variant.price();
                BigDecimal costPerUnit = variant.costPrice() != null ? variant.costPrice() : BigDecimal.ZERO;
                BigDecimal itemDiscount = item.discount() != null ? item.discount() : BigDecimal.ZERO;

                processedItems.add(new ProcessedItem(
                        item.variantId(),
                        variant.name(),
                        variant.sku(),
                        product.name(),
                        item.quantity(),
                        pricePerUnit,
                        costPerUnit,
                        calculatedItem.getTaxRate(),
                        calculatedItem.getTaxAmount(),
                        itemDiscount,
                        calculatedItem.getTaxRuleSnapshot()
                ));
            }

            BigDecimal totalAmount = taxResult.grandTotal();
            BigDecimal paidAmount = payments.stream()
                    .map(PaymentRequest::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String status = determineStatus(paidAmount, totalAmount);
            String fulfillmentStatus = "paid".equals(status) ? "fulfilled" : "pending";

            long primaryPaymentMethodId = payments.isEmpty() ? 0L : payments.get(0).paymentMethodId();
            String invoiceNumber = invoiceNumberService.generate(primaryPaymentMethodId);

            Sale sale = new Sale(
                    null,
                    invoiceNumber,
                    TimeUtil.nowUTC(),
                    totalAmount,
                    paidAmount,
                    discount,
                    totalTax,
                    status,
                    fulfillmentStatus,
                    request.customerId(),
                    userId,
                    null, null, null, null, null, null
            );

            long saleId = salesRepository.insertSale(sale);

            List<SaleItem> insertedItems = new ArrayList<>();
            for (ProcessedItem item : processedItems) {
                SaleItem saleItem = new SaleItem(
                        null,
                        saleId,
                        item.variantId,
                        item.variantName,
                        item.sku,
                        item.productName,
                        item.quantity,
                        item.pricePerUnit,
                        item.costPerUnit,
                        item.taxRate,
                        item.taxAmount,
                        item.taxRate,
                        item.taxAmount,
                        item.taxRuleSnapshot,
                        item.discountAmount,
                        null
                );

                long saleItemId = salesRepository.insertSaleItem(saleItem);

                inventoryService.deductStock(
                        item.variantId,
                        item.quantity,
                        userId,
                        InventoryReason.SALE,
                        "sale_item",
                        saleItemId
                );

                insertedItems.add(new SaleItem(
                        saleItemId,
                        saleId,
                        item.variantId,
                        item.variantName,
                        item.sku,
                        item.productName,
                        item.quantity,
                        item.pricePerUnit,
                        item.costPerUnit,
                        item.taxRate,
                        item.taxAmount,
                        item.taxRate,
                        item.taxAmount,
                        item.taxRuleSnapshot,
                        item.discountAmount,
                        null
                ));
            }

            List<Transaction> insertedTransactions = new ArrayList<>();
            for (PaymentRequest payment : payments) {
                Transaction transaction = new Transaction(
                        null,
                        payment.amount(),
                        "payment",
                        payment.paymentMethodId(),
                        null,
                        "completed",
                        TimeUtil.nowUTC(),
                        invoiceNumber,
                        null, null
                );
                long txId = salesRepository.insertTransaction(transaction, saleId);
                insertedTransactions.add(new Transaction(
                        txId,
                        payment.amount(),
                        "payment",
                        payment.paymentMethodId(),
                        null,
                        "completed",
                        TimeUtil.nowUTC(),
                        invoiceNumber,
                        null, null
                ));
            }

            auditLogger.logDataModification(
                    userId,
                    "CREATE",
                    "sales",
                    saleId,
                    null,
                    String.format("Invoice: %s, Total: %s, Items: %d", 
                            invoiceNumber, totalAmount, processedItems.size())
            );

            Sale createdSale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new RuntimeException("Failed to retrieve created sale"));

            return new SaleResponse(createdSale, insertedItems, insertedTransactions);
        });
    }

    private Map<Long, Variant> fetchVariantsBatch(List<Long> variantIds) {
        Map<Long, Variant> map = new HashMap<>();
        for (Long id : variantIds) {
            variantRepository.findVariantByIdSync(id).ifPresent(v -> map.put(id, v));
        }
        return map;
    }

    private String determineStatus(BigDecimal paidAmount, BigDecimal totalAmount) {
        if (paidAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "draft";
        } else if (paidAmount.compareTo(totalAmount) >= 0) {
            return "paid";
        } else {
            return "partially_paid";
        }
    }

    private boolean isInventoryRestrictionsEnabled() {
        try {
            return settingsStore.loadGeneralSettings().isInventoryAlertsAndRestrictionsEnabled();
        } catch (Exception ex) {
            return true;
        }
    }

    private record TempItem(CreateSaleItemRequest item, BigDecimal pricePerUnit, BigDecimal netLineTotal) {}
    private record ProcessedItem(long variantId, String variantName, String sku, String productName,
                                  int quantity, BigDecimal pricePerUnit, BigDecimal costPerUnit,
                                  BigDecimal taxRate, BigDecimal taxAmount, BigDecimal discountAmount,
                                  String taxRuleSnapshot) {}

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

    public void cancelSale(long saleId, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(
                com.possum.application.auth.Permissions.SALES_MANAGE);
        
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status())) {
                throw new ValidationException("Sale is already cancelled");
            }
            if ("refunded".equals(sale.status())) {
                throw new ValidationException("Cannot cancel a refunded sale");
            }

            List<SaleItem> items = salesRepository.findSaleItems(saleId);
            for (SaleItem item : items) {
                inventoryService.restoreStock(
                        item.variantId(),
                        "sale_item",
                        item.id(),
                        item.quantity(),
                        userId,
                        InventoryReason.CORRECTION,
                        "sale_cancellation",
                        saleId
                );
            }

            salesRepository.updateSaleStatus(saleId, "cancelled");
            salesRepository.updateFulfillmentStatus(saleId, "cancelled");

            auditLogger.logDataModification(
                    userId,
                    "UPDATE",
                    "sales",
                    saleId,
                    "status: " + sale.status(),
                    "status: cancelled"
            );

            return null;
        });
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

    public com.possum.application.sales.dto.SaleStats getSaleStats(com.possum.shared.dto.SaleFilter filter) {
        return salesRepository.getSaleStats(filter);
    }
}

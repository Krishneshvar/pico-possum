package com.possum.application.sales;

import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.dto.*;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.model.*;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.domain.services.SaleCalculator;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckoutService {
    private final SalesRepository salesRepository;
    private final VariantRepository variantRepository;
    private final CustomerRepository customerRepository;
    private final AuditRepository auditRepository;
    private final InventoryService inventoryService;
    private final SaleCalculator saleCalculator;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;
    private final InvoiceNumberService invoiceNumberService;

    public CheckoutService(SalesRepository salesRepository,
                           VariantRepository variantRepository,
                           CustomerRepository customerRepository,
                           AuditRepository auditRepository,
                           InventoryService inventoryService,
                           SaleCalculator saleCalculator,
                           TransactionManager transactionManager,
                           JsonService jsonService,
                           SettingsStore settingsStore,
                           InvoiceNumberService invoiceNumberService) {
        this.salesRepository = salesRepository;
        this.variantRepository = variantRepository;
        this.customerRepository = customerRepository;
        this.auditRepository = auditRepository;
        this.inventoryService = inventoryService;
        this.saleCalculator = saleCalculator;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
        this.invoiceNumberService = invoiceNumberService;
    }

    public SaleResponse createSale(CreateSaleRequest request, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_CREATE);
        request.validate();

        List<Long> variantIds = request.items().stream().map(CreateSaleItemRequest::variantId).toList();
        Map<Long, Variant> variantMap = fetchVariantsBatch(variantIds);

        SaleDraft draft = new SaleDraft();
        if (request.customerId() != null) {
            Customer customer = customerRepository.findCustomerById(request.customerId()).orElse(null);
            draft.setSelectedCustomer(customer);
        }
        
        draft.setOverallDiscountValue(request.discount() != null ? request.discount() : BigDecimal.ZERO);
        draft.setDiscountFixed(true); 

        for (CreateSaleItemRequest itemReq : request.items()) {
            Variant v = variantMap.get(itemReq.variantId());
            if (v == null) {
                throw new NotFoundException("Variant not found: " + itemReq.variantId());
            }
            CartItem cartItem = new CartItem(v, itemReq.quantity());
            cartItem.setPricePerUnit(itemReq.pricePerUnit() != null ? itemReq.pricePerUnit() : v.price());
            cartItem.setDiscountType("fixed");
            cartItem.setDiscountValue(itemReq.discount() != null ? itemReq.discount() : BigDecimal.ZERO);
            
            draft.addItem(cartItem);
        }

        saleCalculator.recalculate(draft);

        long saleId = transactionManager.runInTransaction(() -> {
            boolean enforceInventoryRestrictions = isInventoryRestrictionsEnabled();
            
            long primaryPaymentMethodId = (request.payments() != null && !request.payments().isEmpty())
                    ? request.payments().get(0).paymentMethodId()
                    : 0L;
            String invoiceNumber = invoiceNumberService.generate(primaryPaymentMethodId);
            
            Sale saleEntity = new Sale(
                    null,
                    invoiceNumber,
                    com.possum.shared.util.TimeUtil.nowUTC(),
                    draft.getTotal(),
                    BigDecimal.ZERO,
                    draft.getDiscountTotal(),
                    draft.getTaxAmount(),
                    "draft", 
                    "pending",
                    draft.getSelectedCustomer() != null ? draft.getSelectedCustomer().id() : null,
                    userId,
                    null, null, null, null, null, null
            );

            long newSaleId = salesRepository.insertSale(saleEntity);
            
            for (CartItem cartItem : draft.getItems()) {
                SaleItem item = new SaleItem(
                        null,
                        newSaleId,
                        cartItem.getVariant().id(),
                        cartItem.getVariant().name(),
                        cartItem.getVariant().sku(),
                        cartItem.getVariant().productName(),
                        cartItem.getQuantity(),
                        cartItem.getPricePerUnit(),
                        cartItem.getVariant().costPrice(),
                        cartItem.getTaxRate(),
                        cartItem.getTaxAmount(),
                        cartItem.getTaxRate(), 
                        cartItem.getTaxAmount(), 
                        cartItem.getTaxRuleSnapshot(),
                        cartItem.getDiscountAmount(),
                        null
                );
                salesRepository.insertSaleItem(item);

                if (enforceInventoryRestrictions) {
                    int currentStock = inventoryService.getVariantStock(cartItem.getVariant().id());
                    if (currentStock < cartItem.getQuantity()) {
                        throw new InsufficientStockException(currentStock, cartItem.getQuantity());
                    }
                }

                inventoryService.deductStock(
                        cartItem.getVariant().id(),
                        cartItem.getQuantity(),
                        userId,
                        InventoryReason.SALE,
                        null,
                        newSaleId
                );
            }

            BigDecimal totalPaid = BigDecimal.ZERO;
            if (request.payments() != null) {
                for (PaymentRequest p : request.payments()) {
                    totalPaid = totalPaid.add(p.amount());
                    Transaction transaction = new Transaction(
                            null, p.amount(), "payment", p.paymentMethodId(), 
                            null, "completed", com.possum.shared.util.TimeUtil.nowUTC(), 
                            invoiceNumber, null, null
                    );
                    salesRepository.insertTransaction(transaction, newSaleId);
                }
            }

            String status = "draft";
            if (totalPaid.compareTo(draft.getTotal()) >= 0) status = "paid";
            else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) status = "partially_paid";
            
            salesRepository.updateSaleStatus(newSaleId, status);
            salesRepository.updateSalePaidAmount(newSaleId, totalPaid);
            if (status.equals("paid") || status.equals("partially_paid")) {
                salesRepository.updateFulfillmentStatus(newSaleId, "fulfilled");
            }
            
            auditRepository.log("sales", newSaleId, "CREATE", jsonService.toJson(saleEntity), userId);
            
            return newSaleId;
        });

        Sale saleResult = salesRepository.findSaleById(saleId).orElseThrow();
        List<SaleItem> itemsResult = salesRepository.findSaleItems(saleId);
        List<Transaction> transactionsResult = salesRepository.findTransactionsBySaleId(saleId);

        return new SaleResponse(saleResult, itemsResult, transactionsResult);
    }

    private Map<Long, Variant> fetchVariantsBatch(List<Long> variantIds) {
        Map<Long, Variant> map = new HashMap<>();
        for (Long id : variantIds) {
            variantRepository.findVariantByIdSync(id).ifPresent(v -> map.put(id, v));
        }
        return map;
    }

    private boolean isInventoryRestrictionsEnabled() {
        try {
            return settingsStore.loadGeneralSettings().isInventoryAlertsAndRestrictionsEnabled();
        } catch (Exception ex) {
            return true;
        }
    }
}

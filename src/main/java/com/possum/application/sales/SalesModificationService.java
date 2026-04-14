package com.possum.application.sales;

import com.possum.application.inventory.InventoryService;
import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.application.sales.dto.TaxableItem;
import com.possum.application.sales.dto.UpdateSaleItemRequest;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.InsufficientStockException;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.*;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.domain.services.TaxCalculator;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.shared.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SalesModificationService {
    private final SalesRepository salesRepository;
    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final AuditRepository auditRepository;
    private final InventoryService inventoryService;
    private final TaxCalculator taxCalculator;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;

    public SalesModificationService(SalesRepository salesRepository,
                                    VariantRepository variantRepository,
                                    ProductRepository productRepository,
                                    CustomerRepository customerRepository,
                                    AuditRepository auditRepository,
                                    InventoryService inventoryService,
                                    TaxCalculator taxCalculator,
                                    TransactionManager transactionManager,
                                    JsonService jsonService,
                                    SettingsStore settingsStore) {
        this.salesRepository = salesRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.auditRepository = auditRepository;
        this.inventoryService = inventoryService;
        this.taxCalculator = taxCalculator;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
    }

    public void updateSaleItems(long saleId, List<UpdateSaleItemRequest> itemRequests, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
        
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot edit items for a " + sale.status() + " sale");
            }

            List<SaleItem> oldItems = salesRepository.findSaleItems(saleId);
            
            for (SaleItem oldItem : oldItems) {
                inventoryService.restoreStock(
                        oldItem.variantId(),
                        "sale_item",
                        oldItem.id(),
                        oldItem.quantity(),
                        userId,
                        InventoryReason.CORRECTION,
                        "bill_edit_restoration",
                        saleId
                );
                salesRepository.deleteSaleItem(oldItem.id());
            }

            List<Long> variantIds = itemRequests.stream().map(UpdateSaleItemRequest::variantId).toList();
            Map<Long, Variant> variantMap = fetchVariantsBatch(variantIds);

            boolean enforceInventoryRestrictions = isInventoryRestrictionsEnabled();
            for (UpdateSaleItemRequest req : itemRequests) {
                if (enforceInventoryRestrictions) {
                    int currentStock = inventoryService.getVariantStock(req.variantId());
                    if (currentStock < req.quantity()) {
                        throw new InsufficientStockException(currentStock, req.quantity());
                    }
                }
            }

            List<TaxableItem> itemsToCalculate = new ArrayList<>();
            for (UpdateSaleItemRequest req : itemRequests) {
                Variant v = variantMap.get(req.variantId());
                Product p = productRepository.findProductById(v.productId()).orElseThrow();
                
                itemsToCalculate.add(new TaxableItem(
                        p.name(), v.name(), req.pricePerUnit(), req.quantity(),
                        p.taxCategoryId(), v.id(), p.id()
                ));
            }

            Customer customer = sale.customerId() != null ? customerRepository.findCustomerById(sale.customerId()).orElse(null) : null;
            TaxCalculationResult taxResult = taxCalculator.calculate(new TaxableInvoice(itemsToCalculate), customer);

            for (int i = 0; i < itemRequests.size(); i++) {
                UpdateSaleItemRequest req = itemRequests.get(i);
                TaxableItem calculated = taxResult.getItemByIndex(i);
                Variant v = variantMap.get(req.variantId());
                Product p = productRepository.findProductById(v.productId()).orElseThrow();

                SaleItem item = new SaleItem(
                        null, saleId, v.id(), v.name(), v.sku(), p.name(),
                        req.quantity(), req.pricePerUnit(), v.costPrice(),
                        calculated.getTaxRate(), calculated.getTaxAmount(),
                        calculated.getTaxRate(), calculated.getTaxAmount(),
                        calculated.getTaxRuleSnapshot(), req.discount(), null
                );
                long newItemId = salesRepository.insertSaleItem(item);
                
                inventoryService.deductStock(
                        v.id(), req.quantity(), userId, InventoryReason.SALE,
                        "sale_item", newItemId
                );
            }

            salesRepository.updateSaleTotals(
                    saleId, 
                    taxResult.grandTotal(), 
                    taxResult.totalTax(), 
                    sale.discount()
            );

            Map<String, Object> oldSummary = Map.of("item_count", oldItems.size(), "total", sale.totalAmount());
            Map<String, Object> newSummary = Map.of("item_count", itemRequests.size(), "total", taxResult.grandTotal());
            
            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "sales", saleId,
                    jsonService.toJson(oldSummary), jsonService.toJson(newSummary),
                    jsonService.toJson(Map.of("reason", "Line item correction")),
                    null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
    }

    public void cancelSale(long saleId, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
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

            Map<String, Object> oldData = Map.of("status", sale.status());
            Map<String, Object> newData = Map.of("status", "cancelled");
            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "sales", saleId,
                    jsonService.toJson(oldData), jsonService.toJson(newData),
                    jsonService.toJson(Map.of("reason", "Cancellation")),
                    null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
    }

    public void completeSale(long saleId, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("fulfilled".equals(sale.fulfillmentStatus())) {
                throw new ValidationException("Sale is already fulfilled");
            }
            if ("cancelled".equals(sale.status())) {
                throw new ValidationException("Cannot fulfill a cancelled sale");
            }

            salesRepository.updateFulfillmentStatus(saleId, "fulfilled");

            Map<String, Object> oldData = Map.of("fulfillment_status", sale.fulfillmentStatus());
            Map<String, Object> newData = Map.of("fulfillment_status", "fulfilled");
            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "sales", saleId,
                    jsonService.toJson(oldData), jsonService.toJson(newData),
                    null, null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
    }

    public void changeSalePaymentMethod(long saleId, long newPaymentMethodId, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
        
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot change payment method for a " + sale.status() + " sale");
            }

            if (!salesRepository.paymentMethodExists(newPaymentMethodId)) {
                throw new NotFoundException("Payment method not found: " + newPaymentMethodId);
            }

            List<Transaction> transactions = salesRepository.findTransactionsBySaleId(saleId);
            Transaction primaryTx = transactions.stream()
                    .filter(t -> "payment".equals(t.type()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("No payment transaction found for this sale"));

            if (primaryTx.paymentMethodId() == newPaymentMethodId) {
                return null;
            }

            salesRepository.updateTransactionPaymentMethod(saleId, newPaymentMethodId);

            Map<String, Object> oldData = Map.of("payment_method_id", primaryTx.paymentMethodId());
            Map<String, Object> newData = Map.of("payment_method_id", newPaymentMethodId);
            
            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "transactions", primaryTx.id(),
                    jsonService.toJson(oldData), jsonService.toJson(newData),
                    jsonService.toJson(Map.of("reason", "Payment method correction", "sale_id", saleId)),
                    null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
    }

    public void changeSaleCustomer(long saleId, Long newCustomerId, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SALES_MANAGE);
        
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot change customer for a " + sale.status() + " sale");
            }

            if (Objects.equals(sale.customerId(), newCustomerId)) {
                return null;
            }

            salesRepository.updateSaleCustomer(saleId, newCustomerId);

            Map<String, Object> oldData = Map.of("customer_id", sale.customerId() != null ? sale.customerId() : -1L);
            Map<String, Object> newData = Map.of("customer_id", newCustomerId != null ? newCustomerId : -1L);
            
            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "sales", saleId,
                    jsonService.toJson(oldData), jsonService.toJson(newData),
                    jsonService.toJson(Map.of("reason", "Customer correction")),
                    null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
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

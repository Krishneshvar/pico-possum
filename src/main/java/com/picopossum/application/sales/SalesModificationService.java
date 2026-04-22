package com.picopossum.application.sales;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.sales.dto.UpdateSaleItemRequest;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.exceptions.InsufficientStockException;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.application.audit.AuditService;
import com.picopossum.domain.repositories.*;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.shared.util.TimeUtil;
import com.picopossum.persistence.db.TransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SalesModificationService {
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    private final InventoryService inventoryService;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;

    public SalesModificationService(SalesRepository salesRepository,
                                    ProductRepository productRepository,
                                    CustomerRepository customerRepository,
                                    AuditService auditService,
                                    InventoryService inventoryService,
                                    TransactionManager transactionManager,
                                    JsonService jsonService,
                                    SettingsStore settingsStore) {
        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
    }

    public void updateSaleItems(long saleId, List<UpdateSaleItemRequest> itemRequests) {
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot edit items for a " + sale.status() + " sale");
            }

            List<SaleItem> oldItems = salesRepository.findSaleItems(saleId);
            
            Map<Long, Integer> oldProductTotals = new HashMap<>();
            oldItems.forEach(i -> oldProductTotals.merge(i.productId(), i.quantity(), Integer::sum));
            
            Map<Long, Integer> newProductTotals = new HashMap<>();
            itemRequests.forEach(r -> newProductTotals.merge(r.productId(), r.quantity(), Integer::sum));
            
            java.util.Set<Long> productIds = new java.util.HashSet<>(oldProductTotals.keySet());
            productIds.addAll(newProductTotals.keySet());

            boolean enforceInventoryRestrictions = isInventoryRestrictionsEnabled();
            
            for (Long pid : productIds) {
                int oldQ = oldProductTotals.getOrDefault(pid, 0);
                int newQ = newProductTotals.getOrDefault(pid, 0);
                int diff = newQ - oldQ;
                
                if (diff > 0 && enforceInventoryRestrictions) {
                    int currentStock = inventoryService.getProductStock(pid);
                    if (currentStock < diff) throw new InsufficientStockException(currentStock, diff);
                }
            }

            for (Long pid : productIds) {
                int oldQ = oldProductTotals.getOrDefault(pid, 0);
                int newQ = newProductTotals.getOrDefault(pid, 0);
                int diff = newQ - oldQ;

                if (diff > 0) {
                    inventoryService.deductStock(pid, diff, InventoryReason.SALE, "sale_edit_add", saleId);
                } else if (diff < 0) {
                    // Restore stock via direct adjustment
                    inventoryService.adjustInventory(
                            pid, 
                            Math.abs(diff), 
                            InventoryReason.CORRECTION, 
                            "sale_edit_reduction", 
                            saleId, 
                            "Stock restored after sale item reduction"
                    );
                }
            }

            for (SaleItem oldItem : oldItems) {
                salesRepository.deleteSaleItem(oldItem.id());
            }

            Map<Long, Product> productDetails = fetchProductsBatch(new ArrayList<>(newProductTotals.keySet()));
            BigDecimal newSubtotal = BigDecimal.ZERO;
            for (UpdateSaleItemRequest req : itemRequests) {
                Product p = productDetails.get(req.productId());
                BigDecimal lineNet = req.pricePerUnit().multiply(BigDecimal.valueOf(req.quantity()))
                                     .subtract(req.discount() != null ? req.discount() : BigDecimal.ZERO)
                                     .max(BigDecimal.ZERO);
                newSubtotal = newSubtotal.add(lineNet);

                salesRepository.insertSaleItem(new SaleItem(
                    null, saleId, p.id(), p.sku(), p.name(),
                    req.quantity(), req.pricePerUnit(), p.costPrice(),
                    req.discount(), null
                ));
            }

            BigDecimal totalDiscount = sale.discount() != null ? sale.discount() : BigDecimal.ZERO;
            BigDecimal grandTotal = newSubtotal.subtract(totalDiscount).max(BigDecimal.ZERO);

            salesRepository.updateSaleTotals(saleId, grandTotal, totalDiscount);

            if (sale.totalAmount().compareTo(sale.paidAmount()) == 0) {
                salesRepository.updateSalePaidAmount(saleId, grandTotal);
            }

            auditService.logUpdate("sales", saleId, 
                    Map.of("total", sale.totalAmount()), 
                    Map.of("total", grandTotal),
                    "Line item correction");

            return null;
        });
    }

    public void cancelSale(long saleId) {
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
                inventoryService.adjustInventory(
                        item.productId(),
                        item.quantity(),
                        InventoryReason.RETURN,
                        "sale_cancellation",
                        saleId,
                        "Stock restored from cancelled sale"
                );
            }

            salesRepository.updateSaleStatus(saleId, "cancelled");
            salesRepository.updateFulfillmentStatus(saleId, "cancelled");

            Map<String, Object> oldData = Map.of("status", sale.status());
            Map<String, Object> newData = Map.of("status", "cancelled");
            auditService.recordEvent("UPDATE", "sales", saleId, oldData, newData, "Cancellation", "warning");

            return null;
        });
    }

    public void completeSale(long saleId) {
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
            auditService.logUpdate("sales", saleId, oldData, newData, "Fulfillment");

            return null;
        });
    }

    public void changeSalePaymentMethod(long saleId, long newPaymentMethodId) {
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot change payment method for a " + sale.status() + " sale");
            }

            if (!salesRepository.paymentMethodExists(newPaymentMethodId)) {
                throw new NotFoundException("Payment method not found: " + newPaymentMethodId);
            }

            if (Objects.equals(sale.paymentMethodId(), newPaymentMethodId)) {
                return null;
            }

            salesRepository.updateSalePaymentMethod(saleId, newPaymentMethodId);

            Map<String, Object> oldData = Map.of("payment_method_id", sale.paymentMethodId() != null ? sale.paymentMethodId() : -1L);
            Map<String, Object> newData = Map.of("payment_method_id", newPaymentMethodId);
            
            auditService.logUpdate("sales", saleId, oldData, newData, "Payment method correction");

            return null;
        });
    }

    public void changeSaleCustomer(long saleId, Long newCustomerId) {
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
            
            auditService.logUpdate("sales", saleId, oldData, newData, "Customer correction");

            return null;
        });
    }

    private Map<Long, Product> fetchProductsBatch(List<Long> productIds) {
        Map<Long, Product> map = new HashMap<>();
        for (Long id : productIds) {
            productRepository.findProductById(id).ifPresent(p -> map.put(id, p));
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

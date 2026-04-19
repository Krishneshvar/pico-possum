package com.picopossum.application.sales;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.application.sales.dto.UpdateSaleItemRequest;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.exceptions.InsufficientStockException;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.*;
import com.picopossum.infrastructure.filesystem.SettingsStore;
import com.picopossum.shared.util.TimeUtil;

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
    private final AuditRepository auditRepository;
    private final InventoryService inventoryService;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final SettingsStore settingsStore;

    public SalesModificationService(SalesRepository salesRepository,
                                    ProductRepository productRepository,
                                    CustomerRepository customerRepository,
                                    AuditRepository auditRepository,
                                    InventoryService inventoryService,
                                    TransactionManager transactionManager,
                                    JsonService jsonService,
                                    SettingsStore settingsStore) {
        this.salesRepository = salesRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.auditRepository = auditRepository;
        this.inventoryService = inventoryService;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.settingsStore = settingsStore;
    }

    public void updateSaleItems(long saleId, List<UpdateSaleItemRequest> itemRequests, long userId) {
        transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(saleId)
                    .orElseThrow(() -> new NotFoundException("Sale not found: " + saleId));

            if ("cancelled".equals(sale.status()) || "refunded".equals(sale.status())) {
                throw new ValidationException("Cannot edit items for a " + sale.status() + " sale");
            }

            List<SaleItem> oldItems = salesRepository.findSaleItems(saleId);
            
            // ─── 1. Physical Inventory Delta ───────────────────
            Map<Long, Integer> oldProductTotals = new HashMap<>();
            oldItems.forEach(i -> oldProductTotals.merge(i.productId(), i.quantity(), Integer::sum));
            
            Map<Long, Integer> newProductTotals = new HashMap<>();
            itemRequests.forEach(r -> newProductTotals.merge(r.productId(), r.quantity(), Integer::sum));
            
            java.util.Set<Long> productIds = new java.util.HashSet<>(oldProductTotals.keySet());
            productIds.addAll(newProductTotals.keySet());

            boolean enforceInventoryRestrictions = isInventoryRestrictionsEnabled();
            
            // Verify stock for net additions
            for (Long pid : productIds) {
                int oldQ = oldProductTotals.getOrDefault(pid, 0);
                int newQ = newProductTotals.getOrDefault(pid, 0);
                int diff = newQ - oldQ;
                
                if (diff > 0 && enforceInventoryRestrictions) {
                    int currentStock = inventoryService.getProductStock(pid);
                    if (currentStock < diff) throw new InsufficientStockException(currentStock, diff);
                }
            }

            // Apply stock changes
            for (Long pid : productIds) {
                int oldQ = oldProductTotals.getOrDefault(pid, 0);
                int newQ = newProductTotals.getOrDefault(pid, 0);
                int diff = newQ - oldQ;

                if (diff > 0) {
                    inventoryService.deductStock(pid, diff, userId, InventoryReason.SALE, "sale_edit_add", saleId);
                } else if (diff < 0) {
                    // Find an old item to use as a reference for restoration
                    long refId = oldItems.stream().filter(i -> i.productId() == pid).findFirst().get().id();
                    inventoryService.restoreStock(pid, "sale_item", refId, Math.abs(diff), userId, InventoryReason.CORRECTION, "sale_edit_reduction", saleId);
                }
            }

            // ─── 2. Refresh Invoice Items (Database) ───────────
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

            // ─── 3. Financial Totals & Payments ─────────────
            BigDecimal totalDiscount = sale.discount() != null ? sale.discount() : BigDecimal.ZERO;
            BigDecimal grandTotal = newSubtotal.subtract(totalDiscount).max(BigDecimal.ZERO);

            salesRepository.updateSaleTotals(saleId, grandTotal, totalDiscount);

            // Auto-adjust payment transaction if the bill was settled
            if (sale.totalAmount().compareTo(sale.paidAmount()) == 0) {
                List<Transaction> transactions = salesRepository.findTransactionsBySaleId(saleId);
                transactions.stream()
                    .filter(t -> "payment".equalsIgnoreCase(t.type()) && "completed".equalsIgnoreCase(t.status()))
                    .findFirst()
                    .ifPresent(tx -> {
                        salesRepository.updateTransactionAmount(tx.id(), grandTotal);
                        salesRepository.updateSalePaidAmount(saleId, grandTotal);
                    });
            }

            AuditLog auditLog = new AuditLog(
                    null, userId, "UPDATE", "sales", saleId,
                    jsonService.toJson(Map.of("total", sale.totalAmount())), jsonService.toJson(Map.of("total", grandTotal)),
                    jsonService.toJson(Map.of("reason", "Line item correction")),
                    null, TimeUtil.nowUTC()
            );
            auditRepository.insertAuditLog(auditLog);

            return null;
        });
    }

    public void cancelSale(long saleId, long userId) {
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
                        item.productId(),
                        "sale_item",
                        item.id(),
                        item.quantity(),
                        userId,
                        InventoryReason.RETURN,
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

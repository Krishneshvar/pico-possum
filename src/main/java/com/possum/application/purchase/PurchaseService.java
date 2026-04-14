package com.possum.application.purchase;

import com.possum.domain.enums.FlowEventType;
import com.possum.domain.enums.PurchaseStatus;
import com.possum.domain.enums.TransactionType;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.*;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.PurchaseOrderFilter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import com.possum.shared.util.TimeUtil;
import java.util.*;

public class PurchaseService {
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final VariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductFlowRepository productFlowRepository;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final ConnectionProvider connectionProvider;
    private final JsonService jsonService;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           SupplierRepository supplierRepository,
                           VariantRepository variantRepository,
                           InventoryRepository inventoryRepository,
                           ProductFlowRepository productFlowRepository,
                           AuditRepository auditRepository,
                           TransactionManager transactionManager,
                           ConnectionProvider connectionProvider,
                           JsonService jsonService) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.variantRepository = variantRepository;
        this.inventoryRepository = inventoryRepository;
        this.productFlowRepository = productFlowRepository;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.connectionProvider = connectionProvider;
        this.jsonService = jsonService;
    }

    public PagedResult<PurchaseOrder> getAllPurchaseOrders(PurchaseOrderFilter filter) {
        return purchaseRepository.getAllPurchaseOrders(filter);
    }

    public PurchaseOrderDetail getPurchaseOrderById(long id) {
        PurchaseOrder po = purchaseRepository.getPurchaseOrderById(id)
                .orElseThrow(() -> new NotFoundException("Purchase Order not found"));
        List<PurchaseOrderItem> items = purchaseRepository.getPurchaseOrderItems(id);
        return new PurchaseOrderDetail(po, items);
    }

    public PurchaseOrderDetail createPurchaseOrder(long supplierId, long paymentMethodId, long createdBy, List<PurchaseOrderItemRequest> items) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PURCHASE_MANAGE);
        validateSupplier(supplierId);
        validateItems(items);

        return transactionManager.runInTransaction(() -> {
            String invoiceNumber = generateInvoiceNumber(paymentMethodId);

            List<PurchaseOrderItem> itemsToCreate = items.stream()
                    .map(req -> new PurchaseOrderItem(null, null, req.variantId(), null, null, null, req.quantity(), req.unitCost()))
                    .toList();

            long poId = purchaseRepository.createPurchaseOrder(supplierId, invoiceNumber, paymentMethodId, createdBy, itemsToCreate);

            BigDecimal totalCost = items.stream()
                    .map(item -> item.unitCost().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> auditData = Map.of(
                    "supplier_id", supplierId,
                    "item_count", items.size(),
                    "total_cost", totalCost
            );
            AuditLog auditLog = new AuditLog(null, createdBy, "CREATE", "purchase_orders", poId,
                    null, jsonService.toJson(auditData), null, null, TimeUtil.nowUTC());
            auditRepository.insertAuditLog(auditLog);

            return getPurchaseOrderById(poId);
        });
    }

    public PurchaseOrderDetail updatePurchaseOrder(long id, long supplierId, long paymentMethodId, long updatedBy, List<PurchaseOrderItemRequest> items) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PURCHASE_MANAGE);
        PurchaseOrderDetail existingPo = getPurchaseOrderById(id);
        if (!PurchaseStatus.PENDING.dbValue().equals(existingPo.purchaseOrder().status())) {
            throw new ValidationException("Only pending Purchase Orders can be updated");
        }

        validateSupplier(supplierId);
        validateItems(items);

        return transactionManager.runInTransaction(() -> {
            List<PurchaseOrderItem> itemsToUpdate = items.stream()
                    .map(req -> new PurchaseOrderItem(null, null, req.variantId(), null, null, null, req.quantity(), req.unitCost()))
                    .toList();

            purchaseRepository.updatePurchaseOrder(id, supplierId, paymentMethodId, itemsToUpdate);

            Map<String, Object> oldData = Map.of(
                    "supplier_id", existingPo.purchaseOrder().supplierId(),
                    "item_count", existingPo.items().size()
            );
            Map<String, Object> newData = Map.of(
                    "supplier_id", supplierId,
                    "item_count", items.size()
            );
            AuditLog auditLog = new AuditLog(null, updatedBy, "UPDATE", "purchase_orders", id,
                    jsonService.toJson(oldData), jsonService.toJson(newData), null, null, TimeUtil.nowUTC());
            auditRepository.insertAuditLog(auditLog);

            return getPurchaseOrderById(id);
        });
    }

    public PurchaseOrderDetail receivePurchaseOrder(long id, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PURCHASE_MANAGE);
        PurchaseOrderDetail existingPo = getPurchaseOrderById(id);
        if (!PurchaseStatus.PENDING.dbValue().equals(existingPo.purchaseOrder().status())) {
            throw new ValidationException("Only pending Purchase Orders can be received");
        }

        if (existingPo.items().isEmpty()) {
            throw new ValidationException("Purchase Order has no items");
        }

        return transactionManager.runInTransaction(() -> {
            boolean updated = purchaseRepository.receivePurchaseOrder(id, userId);
            if (!updated) {
                throw new ValidationException("Purchase Order not found or already processed");
            }

            for (PurchaseOrderItem item : existingPo.items()) {
                if (item.quantity() <= 0 || item.unitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Purchase Order has invalid item data");
                }

                InventoryLot lot = new InventoryLot(null, item.variantId(), null, null, null,
                        item.quantity(), item.unitCost(), item.id(), TimeUtil.nowUTC());
                long lotId = inventoryRepository.insertInventoryLot(lot);

                InventoryAdjustment adjustment = new InventoryAdjustment(null, item.variantId(), lotId,
                        item.quantity(), "confirm_receive", "purchase_order_item", item.id(),
                        userId, null, TimeUtil.nowUTC());
                inventoryRepository.insertInventoryAdjustment(adjustment);

                ProductFlow flow = new ProductFlow(null, item.variantId(), FlowEventType.PURCHASE.getValue(),
                        item.quantity(), "purchase_order_item", item.id(), null, null, null, existingPo.purchaseOrder().id(), existingPo.purchaseOrder().invoiceNumber(), null, TimeUtil.nowUTC());
                productFlowRepository.insertProductFlow(flow);
            }

            BigDecimal totalCost = existingPo.items().stream()
                    .map(item -> item.unitCost().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                Long paymentMethodId = existingPo.purchaseOrder().paymentMethodId();
                if (paymentMethodId == null || paymentMethodId <= 0) {
                    paymentMethodId = 1L; // Fallback
                }
                insertPurchaseTransaction(id, totalCost.negate(), paymentMethodId);
            }

            Map<String, Object> oldData = Map.of(
                    "status", existingPo.purchaseOrder().status(),
                    "received_date", existingPo.purchaseOrder().receivedDate() != null ? existingPo.purchaseOrder().receivedDate() : ""
            );
            PurchaseOrderDetail updatedPo = getPurchaseOrderById(id);
            Map<String, Object> newData = Map.of(
                    "status", updatedPo.purchaseOrder().status(),
                    "received_date", updatedPo.purchaseOrder().receivedDate() != null ? updatedPo.purchaseOrder().receivedDate() : ""
            );
            AuditLog auditLog = new AuditLog(null, userId, "UPDATE", "purchase_orders", id,
                    jsonService.toJson(oldData), jsonService.toJson(newData), null, null, TimeUtil.nowUTC());
            auditRepository.insertAuditLog(auditLog);

            return updatedPo;
        });
    }

    public PurchaseOrderDetail cancelPurchaseOrder(long id, long userId) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.PURCHASE_MANAGE);
        PurchaseOrderDetail existingPo = getPurchaseOrderById(id);
        if (!PurchaseStatus.PENDING.dbValue().equals(existingPo.purchaseOrder().status())) {
            throw new ValidationException("Only pending Purchase Orders can be cancelled");
        }

        int rowsAffected = purchaseRepository.cancelPurchaseOrder(id);
        if (rowsAffected == 0) {
            throw new ValidationException("Cannot cancel Purchase Order. It may already be updated by another request.");
        }

        PurchaseOrderDetail updatedPo = getPurchaseOrderById(id);

        Map<String, Object> oldData = Map.of("status", existingPo.purchaseOrder().status());
        Map<String, Object> newData = Map.of("status", updatedPo.purchaseOrder().status());
        AuditLog auditLog = new AuditLog(null, userId, "UPDATE", "purchase_orders", id,
                jsonService.toJson(oldData), jsonService.toJson(newData), null, null, TimeUtil.nowUTC());
        auditRepository.insertAuditLog(auditLog);

        return updatedPo;
    }

    private void validateSupplier(long supplierId) {
        supplierRepository.findSupplierById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier " + supplierId + " not found"));
    }

    private void validateItems(List<PurchaseOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Purchase Order must have at least one item");
        }

        Set<Long> variantIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItemRequest item = items.get(i);

            if (item.quantity() <= 0) {
                throw new ValidationException("items[" + i + "].quantity must be a positive integer");
            }

            if (item.unitCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("items[" + i + "].unit_cost must be a non-negative number");
            }

            if (!variantIds.add(item.variantId())) {
                throw new ValidationException("Duplicate variant_id " + item.variantId() + " is not allowed in a purchase order");
            }

            if (variantRepository.findVariantByIdSync(item.variantId()).isEmpty()) {
                throw new NotFoundException("Variant " + item.variantId() + " not found");
            }
        }
    }

    private String generateInvoiceNumber(long paymentMethodId) {
        String code = "XX";
        if (paymentMethodId > 0) {
            Connection conn = connectionProvider.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT code FROM payment_methods WHERE id = ?")) {
                stmt.setLong(1, paymentMethodId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String fetched = rs.getString("code");
                        if (fetched != null && !fetched.isBlank()) {
                            code = fetched;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        int year = today.getYear();
        String yy = String.format("%02d", year % 100);

        long seq = getNextSequenceForPaymentType("P_GLOBAL_" + year);

        return String.format("P%s%s%07d", yy, code, seq);
    }

    private long getNextSequenceForPaymentType(String paymentTypeCode) {
        Connection conn = connectionProvider.getConnection();
        try {
            try (PreparedStatement upsert = conn.prepareStatement(
                    """
                    INSERT INTO invoice_sequences (payment_type_code, last_sequence)
                    VALUES (?, 1)
                    ON CONFLICT(payment_type_code) DO UPDATE SET last_sequence = last_sequence + 1
                    """)) {
                upsert.setString(1, paymentTypeCode);
                upsert.executeUpdate();
            }
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT last_sequence FROM invoice_sequences WHERE payment_type_code = ?")) {
                select.setString(1, paymentTypeCode);
                try (var rs = select.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("last_sequence");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get next sequence for payment type: " + paymentTypeCode, e);
        }
        throw new IllegalStateException("No sequence row found for payment type: " + paymentTypeCode);
    }

    private void insertPurchaseTransaction(long purchaseOrderId, BigDecimal amount, long paymentMethodId) {
        Connection conn = connectionProvider.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO transactions (purchase_order_id, amount, type, payment_method_id, status) VALUES (?, ?, ?, ?, 'completed')")) {
            stmt.setLong(1, purchaseOrderId);
            stmt.setBigDecimal(2, amount);
            stmt.setString(3, TransactionType.PURCHASE.dbValue());
            stmt.setLong(4, paymentMethodId);
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new com.possum.domain.exceptions.DomainException("Failed to record purchase transaction. Please try again.", e);
        }
    }

    public record PurchaseOrderItemRequest(long variantId, int quantity, BigDecimal unitCost) {}
    public record PurchaseOrderDetail(PurchaseOrder purchaseOrder, List<PurchaseOrderItem> items) {}
}

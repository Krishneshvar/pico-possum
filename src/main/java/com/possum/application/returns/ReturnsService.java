package com.possum.application.returns;

import com.possum.application.inventory.InventoryService;
import com.possum.domain.services.ReturnCalculator;
import com.possum.application.returns.dto.*;
import com.possum.domain.enums.InventoryReason;
import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.*;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.*;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.ReturnFilter;

import java.math.BigDecimal;
import com.possum.shared.util.TimeUtil;
import java.util.*;

public class ReturnsService {
    private final ReturnsRepository returnsRepository;
    private final SalesRepository salesRepository;
    private final InventoryService inventoryService;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final ReturnCalculator returnCalculator;

    public ReturnsService(ReturnsRepository returnsRepository,
                          SalesRepository salesRepository,
                          InventoryService inventoryService,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager,
                          JsonService jsonService,
                          ReturnCalculator returnCalculator) {
        this.returnsRepository = returnsRepository;
        this.salesRepository = salesRepository;
        this.inventoryService = inventoryService;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.returnCalculator = returnCalculator;
    }

    public ReturnResponse createReturn(CreateReturnRequest request) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.RETURNS_MANAGE);
        // Step 1: Input validation
        validateInputs(request);

        return transactionManager.runInTransaction(() -> {
            // Step 2: Sale validation
            Sale sale = salesRepository.findSaleById(request.saleId())
                    .orElseThrow(() -> new NotFoundException("Sale not found"));
            List<SaleItem> saleItems = salesRepository.findSaleItems(request.saleId());

            // Step 3: Aggregate duplicate items
            Map<Long, Integer> aggregatedItems = aggregateDuplicateItems(request.items());

            // Step 4: Validate return quantities
            List<CreateReturnItemRequest> validatedItems = validateReturnQuantities(
                    aggregatedItems, saleItems);

            // Step 5: Calculate refund amounts
            List<RefundCalculation> refundCalculations = returnCalculator.calculateRefunds(
                    validatedItems, saleItems, sale.discount());
            BigDecimal totalRefund = returnCalculator.calculateTotalRefund(refundCalculations);

            // Step 6: Validate refund amount
            validateRefundAmount(totalRefund, sale.paidAmount());

            // Step 7: Create return record
            Return returnRecord = new Return(
                    null,
                    request.saleId(),
                    request.userId(),
                    request.reason().trim(),
                    TimeUtil.nowUTC(),
                    null, null, null, null, null
            );
            long returnId = returnsRepository.insertReturn(returnRecord);

            // Step 8: Create return items and restore inventory
            for (RefundCalculation refundCalc : refundCalculations) {
                ReturnItem returnItem = new ReturnItem(
                        null,
                        returnId,
                        refundCalc.saleItemId(),
                        refundCalc.quantity(),
                        refundCalc.refundAmount(),
                        null, null, null, null, null, null
                );
                long returnItemId = returnsRepository.insertReturnItem(returnItem);


                // Restore inventory
                inventoryService.restoreStock(

                        refundCalc.variantId(),
                        "sale_item",
                        refundCalc.saleItemId(),
                        refundCalc.quantity(),
                        request.userId(),
                        InventoryReason.RETURN,
                        "return_item",
                        returnItemId
                );
            }

            // Step 9: Process sale refund
            processSaleRefund(request.saleId(), totalRefund, request.userId(), sale);

            // Step 10: Audit logging
            Map<String, Object> auditData = Map.of(
                    "sale_id", request.saleId(),
                    "total_refund", totalRefund,
                    "item_count", refundCalculations.size(),
                    "reason", request.reason()
            );
            auditRepository.log("returns", returnId, "CREATE", jsonService.toJson(auditData), request.userId());

            // Step 11: Return result
            return new ReturnResponse(returnId, request.saleId(), totalRefund, refundCalculations.size());
        });
    }

    private void validateInputs(CreateReturnRequest request) {
        if (request.saleId() == null || request.saleId() <= 0) {
            throw new ValidationException("Invalid sale ID");
        }
        if (request.userId() == null || request.userId() <= 0) {
            throw new ValidationException("Invalid user ID");
        }
        if (request.reason() == null || request.reason().trim().isEmpty()) {
            throw new ValidationException("Return reason is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ValidationException("At least one return item is required");
        }

        for (CreateReturnItemRequest item : request.items()) {
            if (item.saleItemId() == null || item.saleItemId() <= 0) {
                throw new ValidationException("Invalid sale item ID: " + item.saleItemId());
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new ValidationException("Invalid return quantity for item " + item.saleItemId());
            }
        }
    }

    private Map<Long, Integer> aggregateDuplicateItems(List<CreateReturnItemRequest> items) {
        Map<Long, Integer> aggregated = new HashMap<>();
        for (CreateReturnItemRequest item : items) {
            aggregated.merge(item.saleItemId(), item.quantity(), Integer::sum);
        }
        return aggregated;
    }

    private List<CreateReturnItemRequest> validateReturnQuantities(
            Map<Long, Integer> aggregatedItems,
            List<SaleItem> saleItems) {

        List<CreateReturnItemRequest> validatedItems = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : aggregatedItems.entrySet()) {
            Long saleItemId = entry.getKey();
            Integer requestedQuantity = entry.getValue();

            SaleItem saleItem = saleItems.stream()
                    .filter(si -> si.id().equals(saleItemId))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Sale item " + saleItemId + " not found in sale"));

            int alreadyReturned = returnsRepository.getTotalReturnedQuantity(saleItemId);
            int availableToReturn = saleItem.quantity() - alreadyReturned;

            if (requestedQuantity > availableToReturn) {
                throw new ValidationException(
                        String.format("Cannot return %d of %s. Only %d remaining to return.",
                                requestedQuantity, saleItem.variantName(), availableToReturn)
                );
            }

            validatedItems.add(new CreateReturnItemRequest(saleItemId, requestedQuantity));
        }

        return validatedItems;
    }

    private void validateRefundAmount(BigDecimal totalRefund, BigDecimal paidAmount) {
        if (totalRefund.compareTo(paidAmount) > 0) {
            throw new ValidationException(
                    String.format("Cannot refund %.2f. Maximum refundable amount is %.2f.",
                            totalRefund, paidAmount)
            );
        }
    }

    private void processSaleRefund(Long saleId, BigDecimal refundAmount, Long userId, Sale sale) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be positive");
        }

        if (refundAmount.compareTo(sale.paidAmount()) > 0) {
            throw new ValidationException(
                    String.format("Cannot refund %.2f. Maximum refundable amount is %.2f.",
                            refundAmount, sale.paidAmount())
            );
        }

        // Determine payment method
        List<Transaction> transactions = salesRepository.findTransactionsBySaleId(saleId);
        Transaction paymentTx = transactions.stream()
                .filter(t -> "payment".equals(t.type()) && "completed".equals(t.status()))
                .findFirst()
                .orElse(null);

        List<PaymentMethod> activeMethods = salesRepository.findPaymentMethods();
        Long fallbackPaymentMethodId = activeMethods.isEmpty() ? 1L : activeMethods.get(0).id();
        Long paymentMethodId = (paymentTx != null && paymentTx.paymentMethodId() != null
                && salesRepository.paymentMethodExists(paymentTx.paymentMethodId()))
                ? paymentTx.paymentMethodId()
                : fallbackPaymentMethodId;

        // Create refund transaction
        Transaction refundTransaction = new Transaction(
                null,
                refundAmount.negate(),
                "refund",
                paymentMethodId,
                null,
                "completed",
                TimeUtil.nowUTC(),
                sale.invoiceNumber(),
                null, null
        );
        salesRepository.insertTransaction(refundTransaction, saleId);

        // Update sale paid amount
        BigDecimal newPaidAmount = sale.paidAmount().subtract(refundAmount);
        salesRepository.updateSalePaidAmount(saleId, newPaidAmount);

        // Update sale status based on refund
        if (newPaidAmount.compareTo(BigDecimal.ZERO) <= 0 && sale.totalAmount().compareTo(BigDecimal.ZERO) > 0) {
            salesRepository.updateSaleStatus(saleId, "refunded");
        } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            salesRepository.updateSaleStatus(saleId, "partially_refunded");
        }
    }

    public Return getReturn(long id) {
        Return returnRecord = returnsRepository.findReturnById(id)
                .orElseThrow(() -> new NotFoundException("Return not found: " + id));
        return returnRecord;
    }

    public List<Return> getSaleReturns(long saleId) {
        if (saleId <= 0) {
            throw new ValidationException("Invalid sale ID");
        }
        return returnsRepository.findReturnsBySaleId(saleId);
    }

    public PagedResult<Return> getReturns(ReturnFilter filter) {
        return returnsRepository.findReturns(filter);
    }
}

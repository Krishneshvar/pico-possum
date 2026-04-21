package com.picopossum.application.returns;

import com.picopossum.application.inventory.InventoryService;
import com.picopossum.domain.services.ReturnCalculator;
import com.picopossum.application.returns.dto.*;
import com.picopossum.domain.enums.InventoryReason;
import com.picopossum.domain.exceptions.NotFoundException;
import com.picopossum.domain.exceptions.ValidationException;
import com.picopossum.domain.model.*;
import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.persistence.db.TransactionManager;
import com.picopossum.domain.repositories.*;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.ReturnFilter;
import com.picopossum.application.sales.InvoiceNumberService;

import java.math.BigDecimal;
import com.picopossum.shared.util.TimeUtil;
import java.util.*;

public class ReturnsService {
    private final ReturnsRepository returnsRepository;
    private final SalesRepository salesRepository;
    private final InventoryService inventoryService;
    private final AuditRepository auditRepository;
    private final TransactionManager transactionManager;
    private final JsonService jsonService;
    private final ReturnCalculator returnCalculator;
    private final InvoiceNumberService invoiceNumberService;

    public ReturnsService(ReturnsRepository returnsRepository,
                          SalesRepository salesRepository,
                          InventoryService inventoryService,
                          AuditRepository auditRepository,
                          TransactionManager transactionManager,
                          JsonService jsonService,
                          ReturnCalculator returnCalculator,
                          InvoiceNumberService invoiceNumberService) {
        this.returnsRepository = returnsRepository;
        this.salesRepository = salesRepository;
        this.inventoryService = inventoryService;
        this.auditRepository = auditRepository;
        this.transactionManager = transactionManager;
        this.jsonService = jsonService;
        this.returnCalculator = returnCalculator;
        this.invoiceNumberService = invoiceNumberService;
    }

    public ReturnResponse createReturn(CreateReturnRequest request) {
        validateInputs(request);

        return transactionManager.runInTransaction(() -> {
            Sale sale = salesRepository.findSaleById(request.saleId())
                    .orElseThrow(() -> new NotFoundException("Sale not found"));
            List<SaleItem> saleItems = salesRepository.findSaleItems(request.saleId());

            Map<Long, Integer> aggregatedItems = aggregateDuplicateItems(request.items());

            List<CreateReturnItemRequest> validatedItems = validateReturnQuantities(
                    aggregatedItems, saleItems);

            List<RefundCalculation> refundCalculations = returnCalculator.calculateRefunds(
                    validatedItems, saleItems, sale.discount());
            BigDecimal totalRefund = returnCalculator.calculateTotalRefund(refundCalculations);
            
            Long paymentMethodId = sale.paymentMethodId();
            if (paymentMethodId == null || paymentMethodId <= 0) {
                List<PaymentMethod> activeMethods = salesRepository.findPaymentMethods();
                paymentMethodId = activeMethods.isEmpty() ? 1L : activeMethods.get(0).id();
            }

            validateRefundAmount(totalRefund, sale.paidAmount());

            String returnInvoiceId = invoiceNumberService.generate("R", paymentMethodId);

            Return returnRecord = new Return(
                    null,
                    request.saleId(),
                    request.reason().trim(),
                    TimeUtil.nowUTC(),
                    null, null, totalRefund, paymentMethodId, null,
                    returnInvoiceId
            );
            long returnId = returnsRepository.insertReturn(returnRecord);

            for (RefundCalculation refundCalc : refundCalculations) {
                ReturnItem returnItem = new ReturnItem(
                        null,
                        returnId,
                        refundCalc.saleItemId(),
                        refundCalc.quantity(),
                        refundCalc.refundAmount(),
                        refundCalc.productId(),
                        refundCalc.pricePerUnit(),
                        refundCalc.sku(),
                        refundCalc.productName()
                );
                long returnItemId = returnsRepository.insertReturnItem(returnItem);

                // Restore inventory via direct movement
                inventoryService.adjustInventory(
                        refundCalc.productId(),
                        refundCalc.quantity(),
                        InventoryReason.RETURN,
                        "return_item",
                        returnItemId,
                        "Stock restored from return " + returnInvoiceId
                );
            }

            processSaleRefund(request.saleId(), totalRefund, sale);

            Map<String, Object> auditData = Map.of(
                    "sale_id", request.saleId(),
                    "total_refund", totalRefund,
                    "item_count", refundCalculations.size(),
                    "reason", request.reason()
            );
            auditRepository.log("returns", returnId, "CREATE", jsonService.toJson(auditData));

            return new ReturnResponse(returnId, request.saleId(), totalRefund, refundCalculations.size());
        });
    }

    private void validateInputs(CreateReturnRequest request) {
        if (request.saleId() == null || request.saleId() <= 0) {
            throw new ValidationException("Invalid sale ID");
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
                                requestedQuantity, saleItem.productName(), availableToReturn)
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

    private void processSaleRefund(Long saleId, BigDecimal refundAmount, Sale sale) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be positive");
        }

        if (refundAmount.compareTo(sale.paidAmount()) > 0) {
            throw new ValidationException(
                    String.format("Cannot refund %.2f. Maximum refundable amount is %.2f.",
                            refundAmount, sale.paidAmount())
            );
        }

        BigDecimal newPaidAmount = sale.paidAmount().subtract(refundAmount);
        salesRepository.updateSalePaidAmount(saleId, newPaidAmount);

        if (newPaidAmount.compareTo(BigDecimal.ZERO) <= 0 && sale.totalAmount().compareTo(BigDecimal.ZERO) > 0) {
            salesRepository.updateSaleStatus(saleId, "refunded");
        } else if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            salesRepository.updateSaleStatus(saleId, "partially_refunded");
        }
    }

    public Return getReturn(long id) {
        return returnsRepository.findReturnById(id)
                .orElseThrow(() -> new NotFoundException("Return not found: " + id));
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

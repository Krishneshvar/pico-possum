package com.possum.domain.services;

import com.possum.domain.enums.InventoryReason;
import com.possum.domain.model.InventoryAdjustment;
import com.possum.shared.dto.AvailableLot;
import com.possum.shared.util.TimeUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Domain service for inventory logic (FIFO deduction, restorations).
 * This class is pure logic and does not interact with the database.
 */
public class StockManager {

    /**
     * Plans a FIFO-based stock deduction.
     */
    public List<InventoryAdjustment> planDeduction(long variantId, int quantity, List<AvailableLot> availableLots, 
                                                   InventoryReason reason, String referenceType, Long referenceId, long userId) {
        int remainingToDeduct = quantity;
        List<InventoryAdjustment> adjustments = new ArrayList<>();

        for (AvailableLot lot : availableLots) {
            if (remainingToDeduct <= 0) break;

            int deductionFromThisLot = Math.min(remainingToDeduct, lot.remainingQuantity());

            adjustments.add(new InventoryAdjustment(
                    null, variantId, lot.id(),
                    -deductionFromThisLot, reason.getValue(), referenceType, referenceId,
                    userId, null, TimeUtil.nowUTC()
            ));

            remainingToDeduct -= deductionFromThisLot;
        }

        // Handle case where we deduct more than what's in lots (headless adjustment)
        if (remainingToDeduct > 0) {
            adjustments.add(new InventoryAdjustment(
                    null, variantId, null,
                    -remainingToDeduct, reason.getValue(), referenceType, referenceId,
                    userId, null, TimeUtil.nowUTC()
            ));
        }

        return adjustments;
    }

    /**
     * Plans a stock restoration based on original adjustments (e.g. for returns).
     */
    public List<InventoryAdjustment> planRestoration(long variantId, int quantity, List<InventoryAdjustment> originalAdjustments,
                                                     InventoryReason reason, String referenceType, Long referenceId, long userId) {
        // Sort logic: restore to the most recent deduction first
        List<InventoryAdjustment> sortedOriginals = new ArrayList<>(originalAdjustments);
        sortedOriginals.sort(Comparator.comparing(InventoryAdjustment::adjustedAt).reversed());

        int remainingToRestore = quantity;
        List<InventoryAdjustment> restorationAdjustments = new ArrayList<>();

        for (InventoryAdjustment adj : sortedOriginals) {
            if (remainingToRestore <= 0) break;

            int originalDeduction = Math.abs(adj.quantityChange());
            int restoreToThisLot = Math.min(remainingToRestore, originalDeduction);

            restorationAdjustments.add(new InventoryAdjustment(
                    null, variantId, adj.lotId(),
                    restoreToThisLot, reason.getValue(), referenceType, referenceId,
                    userId, null, TimeUtil.nowUTC()
            ));

            remainingToRestore -= restoreToThisLot;
        }

        // If something remains (e.g. original deductions were missing), add a headless restoration
        if (remainingToRestore > 0) {
            restorationAdjustments.add(new InventoryAdjustment(
                    null, variantId, null,
                    remainingToRestore, reason.getValue(), referenceType, referenceId,
                    userId, null, TimeUtil.nowUTC()
            ));
        }

        return restorationAdjustments;
    }
}

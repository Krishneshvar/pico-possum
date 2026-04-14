package com.possum.application.taxes;

import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.TaxExemption;
import com.possum.infrastructure.logging.AuditLogger;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.domain.repositories.TaxExemptionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for bulk tax exemption operations.
 * Optimized for processing multiple exemptions efficiently.
 */
public class BulkTaxExemptionService {
    
    private final TaxExemptionRepository taxExemptionRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogger auditLogger;
    private final TaxExemptionService taxExemptionService;
    
    public BulkTaxExemptionService(
            TaxExemptionRepository taxExemptionRepository,
            CustomerRepository customerRepository,
            AuditLogger auditLogger,
            TaxExemptionService taxExemptionService
    ) {
        this.taxExemptionRepository = taxExemptionRepository;
        this.customerRepository = customerRepository;
        this.auditLogger = auditLogger;
        this.taxExemptionService = taxExemptionService;
    }
    
    /**
     * Creates multiple tax exemptions in a single operation.
     * Returns results for each exemption (success or failure).
     */
    public BulkExemptionResult createBulk(List<BulkExemptionRequest> requests, Long approvedBy) {
        List<TaxExemption> created = new ArrayList<>();
        List<BulkExemptionError> errors = new ArrayList<>();
        
        for (int i = 0; i < requests.size(); i++) {
            BulkExemptionRequest request = requests.get(i);
            try {
                TaxExemption exemption = taxExemptionService.createExemption(
                        request.customerId(),
                        request.exemptionType(),
                        request.certificateNumber(),
                        request.reason(),
                        request.validFrom(),
                        request.validTo(),
                        approvedBy
                );
                created.add(exemption);
            } catch (Exception e) {
                errors.add(new BulkExemptionError(i, request.customerId(), e.getMessage()));
            }
        }
        
        auditLogger.logDataModification(
                approvedBy,
                "BULK_CREATE",
                "tax_exemptions",
                null,
                null,
                String.format("Bulk created %d exemptions, %d errors", created.size(), errors.size())
        );
        
        return new BulkExemptionResult(created, errors);
    }
    
    /**
     * Expires multiple tax exemptions by setting their validTo date to now.
     */
    public BulkExemptionResult expireBulk(List<Long> exemptionIds, Long userId) {
        List<TaxExemption> expired = new ArrayList<>();
        List<BulkExemptionError> errors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < exemptionIds.size(); i++) {
            Long exemptionId = exemptionIds.get(i);
            try {
                TaxExemption existing = taxExemptionRepository.findById(exemptionId)
                        .orElseThrow(() -> new ValidationException("Exemption not found: " + exemptionId));
                
                TaxExemption updated = new TaxExemption(
                        exemptionId,
                        existing.customerId(),
                        existing.exemptionType(),
                        existing.certificateNumber(),
                        existing.reason(),
                        existing.validFrom(),
                        now,  // Expire now
                        existing.approvedBy(),
                        existing.createdAt(),
                        null
                );
                
                TaxExemption saved = taxExemptionRepository.save(updated);
                expired.add(saved);
            } catch (Exception e) {
                errors.add(new BulkExemptionError(i, exemptionId, e.getMessage()));
            }
        }
        
        auditLogger.logDataModification(
                userId,
                "BULK_EXPIRE",
                "tax_exemptions",
                null,
                null,
                String.format("Bulk expired %d exemptions, %d errors", expired.size(), errors.size())
        );
        
        return new BulkExemptionResult(expired, errors);
    }
    
    /**
     * Deletes multiple tax exemptions.
     */
    public BulkExemptionResult deleteBulk(List<Long> exemptionIds, Long userId) {
        List<TaxExemption> deleted = new ArrayList<>();
        List<BulkExemptionError> errors = new ArrayList<>();
        
        for (int i = 0; i < exemptionIds.size(); i++) {
            Long exemptionId = exemptionIds.get(i);
            try {
                TaxExemption exemption = taxExemptionRepository.findById(exemptionId)
                        .orElseThrow(() -> new ValidationException("Exemption not found: " + exemptionId));
                
                taxExemptionRepository.delete(exemptionId);
                deleted.add(exemption);
            } catch (Exception e) {
                errors.add(new BulkExemptionError(i, exemptionId, e.getMessage()));
            }
        }
        
        auditLogger.logDataModification(
                userId,
                "BULK_DELETE",
                "tax_exemptions",
                null,
                null,
                String.format("Bulk deleted %d exemptions, %d errors", deleted.size(), errors.size())
        );
        
        return new BulkExemptionResult(deleted, errors);
    }
    
    /**
     * Extends the validity period for multiple exemptions.
     */
    public BulkExemptionResult extendBulk(List<Long> exemptionIds, LocalDateTime newValidTo, Long userId) {
        if (newValidTo == null) {
            throw new ValidationException("New valid to date is required");
        }
        
        List<TaxExemption> extended = new ArrayList<>();
        List<BulkExemptionError> errors = new ArrayList<>();
        
        for (int i = 0; i < exemptionIds.size(); i++) {
            Long exemptionId = exemptionIds.get(i);
            try {
                TaxExemption existing = taxExemptionRepository.findById(exemptionId)
                        .orElseThrow(() -> new ValidationException("Exemption not found: " + exemptionId));
                
                if (existing.validFrom() != null && newValidTo.isBefore(existing.validFrom())) {
                    throw new ValidationException("New valid to date must be after valid from date");
                }
                
                TaxExemption updated = new TaxExemption(
                        exemptionId,
                        existing.customerId(),
                        existing.exemptionType(),
                        existing.certificateNumber(),
                        existing.reason(),
                        existing.validFrom(),
                        newValidTo,
                        existing.approvedBy(),
                        existing.createdAt(),
                        null
                );
                
                TaxExemption saved = taxExemptionRepository.save(updated);
                extended.add(saved);
            } catch (Exception e) {
                errors.add(new BulkExemptionError(i, exemptionId, e.getMessage()));
            }
        }
        
        auditLogger.logDataModification(
                userId,
                "BULK_EXTEND",
                "tax_exemptions",
                null,
                null,
                String.format("Bulk extended %d exemptions to %s, %d errors", 
                        extended.size(), newValidTo, errors.size())
        );
        
        return new BulkExemptionResult(extended, errors);
    }
    
    /**
     * Finds all exemptions expiring within the specified number of days.
     */
    public List<TaxExemption> findExpiringWithinDays(int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(days);
        
        // This would need a repository method to efficiently query
        // For now, we'll return empty list as placeholder
        return List.of();
    }
    
    // DTOs
    public record BulkExemptionRequest(
            Long customerId,
            String exemptionType,
            String certificateNumber,
            String reason,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {}
    
    public record BulkExemptionResult(
            List<TaxExemption> successful,
            List<BulkExemptionError> errors
    ) {
        public int successCount() {
            return successful.size();
        }
        
        public int errorCount() {
            return errors.size();
        }
        
        public int totalCount() {
            return successCount() + errorCount();
        }
        
        public double successRate() {
            return totalCount() > 0 ? (double) successCount() / totalCount() * 100 : 100.0;
        }
    }
    
    public record BulkExemptionError(
            int index,
            Long identifier,
            String errorMessage
    ) {}
}

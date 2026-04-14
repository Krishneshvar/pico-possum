package com.possum.application.taxes;

import com.possum.domain.exceptions.NotFoundException;
import com.possum.domain.exceptions.ValidationException;
import com.possum.domain.model.Customer;
import com.possum.domain.model.TaxExemption;
import com.possum.infrastructure.logging.AuditLogger;
import com.possum.domain.repositories.CustomerRepository;
import com.possum.domain.repositories.TaxExemptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class TaxExemptionService {
    private final TaxExemptionRepository taxExemptionRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogger auditLogger;

    public TaxExemptionService(
            TaxExemptionRepository taxExemptionRepository,
            CustomerRepository customerRepository,
            AuditLogger auditLogger
    ) {
        this.taxExemptionRepository = taxExemptionRepository;
        this.customerRepository = customerRepository;
        this.auditLogger = auditLogger;
    }

    public TaxExemption createExemption(
            Long customerId,
            String exemptionType,
            String certificateNumber,
            String reason,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            Long approvedBy
    ) {
        validateExemptionRequest(customerId, exemptionType, reason, validFrom, validTo);

        Customer customer = customerRepository.findCustomerById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        TaxExemption exemption = new TaxExemption(
                null,
                customerId,
                exemptionType,
                certificateNumber,
                reason,
                validFrom != null ? validFrom : LocalDateTime.now(),
                validTo,
                approvedBy,
                null,
                null
        );

        TaxExemption created = taxExemptionRepository.save(exemption);

        auditLogger.logDataModification(
                approvedBy,
                "CREATE",
                "tax_exemptions",
                created.id(),
                null,
                String.format("Created tax exemption for customer %s (%s)", customer.name(), exemptionType)
        );

        return created;
    }

    public TaxExemption updateExemption(
            Long exemptionId,
            String exemptionType,
            String certificateNumber,
            String reason,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            Long userId
    ) {
        TaxExemption existing = taxExemptionRepository.findById(exemptionId)
                .orElseThrow(() -> new NotFoundException("Tax exemption not found: " + exemptionId));

        validateExemptionRequest(existing.customerId(), exemptionType, reason, validFrom, validTo);

        TaxExemption updated = new TaxExemption(
                exemptionId,
                existing.customerId(),
                exemptionType,
                certificateNumber,
                reason,
                validFrom,
                validTo,
                existing.approvedBy(),
                existing.createdAt(),
                null
        );

        TaxExemption saved = taxExemptionRepository.save(updated);

        auditLogger.logDataModification(
                userId,
                "UPDATE",
                "tax_exemptions",
                exemptionId,
                String.format("Type: %s, Cert: %s", existing.exemptionType(), existing.certificateNumber()),
                String.format("Type: %s, Cert: %s", exemptionType, certificateNumber)
        );

        return saved;
    }

    public void deleteExemption(Long exemptionId, Long userId) {
        TaxExemption exemption = taxExemptionRepository.findById(exemptionId)
                .orElseThrow(() -> new NotFoundException("Tax exemption not found: " + exemptionId));

        taxExemptionRepository.delete(exemptionId);

        auditLogger.logDataModification(
                userId,
                "DELETE",
                "tax_exemptions",
                exemptionId,
                String.format("Customer: %d, Type: %s", exemption.customerId(), exemption.exemptionType()),
                null
        );
    }

    public List<TaxExemption> getCustomerExemptions(Long customerId) {
        return taxExemptionRepository.findByCustomerId(customerId);
    }

    public Optional<TaxExemption> getActiveExemption(Long customerId) {
        return taxExemptionRepository.findActiveExemption(customerId, LocalDateTime.now());
    }

    public Optional<TaxExemption> getExemptionById(Long exemptionId) {
        return taxExemptionRepository.findById(exemptionId);
    }

    private void validateExemptionRequest(
            Long customerId,
            String exemptionType,
            String reason,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {
        if (customerId == null) {
            throw new ValidationException("Customer ID is required");
        }

        if (exemptionType == null || exemptionType.isBlank()) {
            throw new ValidationException("Exemption type is required");
        }

        List<String> validTypes = List.of("government", "ngo", "diplomatic", "export", "other");
        if (!validTypes.contains(exemptionType.toLowerCase())) {
            throw new ValidationException("Invalid exemption type. Must be one of: " + String.join(", ", validTypes));
        }

        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Exemption reason is required");
        }

        if (validFrom != null && validTo != null && validFrom.isAfter(validTo)) {
            throw new ValidationException("Valid from date must be before valid to date");
        }
    }
}

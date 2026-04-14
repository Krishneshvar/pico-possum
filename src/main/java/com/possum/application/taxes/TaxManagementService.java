package com.possum.application.taxes;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.domain.repositories.TaxRepository;

import java.util.List;
import java.util.Optional;

public class TaxManagementService {
    private final TaxRepository taxRepository;

    public TaxManagementService(TaxRepository taxRepository) {
        this.taxRepository = taxRepository;
    }

    public Optional<TaxProfile> getActiveTaxProfile() {
        return taxRepository.getActiveTaxProfile();
    }

    public List<TaxProfile> getAllTaxProfiles() {
        return taxRepository.getAllTaxProfiles();
    }

    public long createTaxProfile(TaxProfile profile) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (profile.name() == null || profile.name().isBlank()) throw new com.possum.domain.exceptions.ValidationException("Tax profile name is required");
        return taxRepository.createTaxProfile(profile);
    }

    public int updateTaxProfile(long id, TaxProfile profile) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (profile.name() == null || profile.name().isBlank()) throw new com.possum.domain.exceptions.ValidationException("Tax profile name is required");
        return taxRepository.updateTaxProfile(id, profile);
    }

    public int deleteTaxProfile(long id) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        return taxRepository.deleteTaxProfile(id);
    }

    public List<TaxCategory> getAllTaxCategories() {
        return taxRepository.getAllTaxCategories();
    }

    public long createTaxCategory(String name, String description) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Tax category name is required");
        return taxRepository.createTaxCategory(name, description);
    }

    public int updateTaxCategory(long id, String name, String description) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (name == null || name.isBlank()) throw new com.possum.domain.exceptions.ValidationException("Tax category name is required");
        return taxRepository.updateTaxCategory(id, name, description);
    }

    public int deleteTaxCategory(long id) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        return taxRepository.deleteTaxCategory(id);
    }

    public List<TaxRule> getTaxRulesByProfileId(long profileId) {
        return taxRepository.getTaxRulesByProfileId(profileId);
    }

    public long createTaxRule(TaxRule rule) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (rule.ratePercent() == null) throw new com.possum.domain.exceptions.ValidationException("Tax rate is required");
        if (rule.ratePercent().compareTo(java.math.BigDecimal.ZERO) < 0 || rule.ratePercent().compareTo(new java.math.BigDecimal("100")) > 0) throw new com.possum.domain.exceptions.ValidationException("Tax rate must be between 0 and 100");
        if (rule.taxCategoryId() == null) throw new com.possum.domain.exceptions.ValidationException("Tax category is required");
        return taxRepository.createTaxRule(rule);
    }

    public int updateTaxRule(long id, TaxRule rule) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        if (rule.ratePercent() == null) throw new com.possum.domain.exceptions.ValidationException("Tax rate is required");
        if (rule.ratePercent().compareTo(java.math.BigDecimal.ZERO) < 0 || rule.ratePercent().compareTo(new java.math.BigDecimal("100")) > 0) throw new com.possum.domain.exceptions.ValidationException("Tax rate must be between 0 and 100");
        if (rule.taxCategoryId() == null) throw new com.possum.domain.exceptions.ValidationException("Tax category is required");
        return taxRepository.updateTaxRule(id, rule);
    }

    public int deleteTaxRule(long id) {
        com.possum.application.auth.ServiceSecurity.requirePermission(com.possum.application.auth.Permissions.SETTINGS_MANAGE);
        return taxRepository.deleteTaxRule(id);
    }
}

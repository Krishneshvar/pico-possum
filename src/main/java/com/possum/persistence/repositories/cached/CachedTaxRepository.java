package com.possum.persistence.repositories.cached;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.infrastructure.caching.CacheManager;
import com.possum.domain.repositories.TaxRepository;

import java.util.List;
import java.util.Optional;

public final class CachedTaxRepository implements TaxRepository {
    
    private final TaxRepository delegate;
    private final CacheManager cacheManager;
    
    public CachedTaxRepository(TaxRepository delegate, CacheManager cacheManager) {
        this.delegate = delegate;
        this.cacheManager = cacheManager;
    }
    
    @Override
    public Optional<TaxProfile> getActiveTaxProfile() {
        return cacheManager.getOrCompute("tax_rules", "active_profile", 
            delegate::getActiveTaxProfile);
    }
    
    @Override
    public List<TaxProfile> getAllTaxProfiles() {
        return cacheManager.getOrCompute("tax_rules", "all_profiles",
            delegate::getAllTaxProfiles);
    }
    
    @Override
    public long createTaxProfile(TaxProfile profile) {
        long id = delegate.createTaxProfile(profile);
        invalidateTaxCaches();
        return id;
    }
    
    @Override
    public int updateTaxProfile(long id, TaxProfile profile) {
        int result = delegate.updateTaxProfile(id, profile);
        if (result > 0) {
            invalidateTaxCaches();
        }
        return result;
    }
    
    @Override
    public int deleteTaxProfile(long id) {
        int result = delegate.deleteTaxProfile(id);
        if (result > 0) {
            invalidateTaxCaches();
        }
        return result;
    }
    
    @Override
    public List<TaxCategory> getAllTaxCategories() {
        return cacheManager.getOrCompute("categories", "tax_categories",
            delegate::getAllTaxCategories);
    }
    
    @Override
    public long createTaxCategory(String name, String description) {
        long id = delegate.createTaxCategory(name, description);
        cacheManager.invalidateAll("categories");
        return id;
    }
    
    @Override
    public int updateTaxCategory(long id, String name, String description) {
        int result = delegate.updateTaxCategory(id, name, description);
        if (result > 0) {
            cacheManager.invalidateAll("categories");
        }
        return result;
    }
    
    @Override
    public int deleteTaxCategory(long id) {
        int result = delegate.deleteTaxCategory(id);
        if (result > 0) {
            cacheManager.invalidateAll("categories");
        }
        return result;
    }
    
    @Override
    public List<TaxRule> getTaxRulesByProfileId(long profileId) {
        return cacheManager.getOrCompute("tax_rules", "profile_" + profileId,
            () -> delegate.getTaxRulesByProfileId(profileId));
    }
    
    @Override
    public long createTaxRule(TaxRule rule) {
        long id = delegate.createTaxRule(rule);
        invalidateTaxCaches();
        return id;
    }
    
    @Override
    public int updateTaxRule(long id, TaxRule rule) {
        int result = delegate.updateTaxRule(id, rule);
        if (result > 0) {
            invalidateTaxCaches();
        }
        return result;
    }
    
    @Override
    public int deleteTaxRule(long id) {
        int result = delegate.deleteTaxRule(id);
        if (result > 0) {
            invalidateTaxCaches();
        }
        return result;
    }
    
    private void invalidateTaxCaches() {
        cacheManager.invalidateAll("tax_rules");
        cacheManager.invalidateAll("categories");
    }
}

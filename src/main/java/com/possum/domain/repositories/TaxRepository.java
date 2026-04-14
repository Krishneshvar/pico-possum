package com.possum.domain.repositories;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;

import java.util.List;
import java.util.Optional;

public interface TaxRepository {
    Optional<TaxProfile> getActiveTaxProfile();

    List<TaxProfile> getAllTaxProfiles();

    long createTaxProfile(TaxProfile profile);

    int updateTaxProfile(long id, TaxProfile profile);

    int deleteTaxProfile(long id);

    List<TaxCategory> getAllTaxCategories();

    long createTaxCategory(String name, String description);

    int updateTaxCategory(long id, String name, String description);

    int deleteTaxCategory(long id);

    List<TaxRule> getTaxRulesByProfileId(long profileId);

    long createTaxRule(TaxRule rule);

    int updateTaxRule(long id, TaxRule rule);

    int deleteTaxRule(long id);
}

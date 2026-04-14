package com.possum.persistence.repositories.cached;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.infrastructure.caching.CacheManager;
import com.possum.domain.repositories.TaxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CachedTaxRepositoryTest {

    private TaxRepository delegate;
    private CacheManager cacheManager;
    private CachedTaxRepository cachedRepository;

    @BeforeEach
    void setUp() {
        delegate = mock(TaxRepository.class);
        cacheManager = mock(CacheManager.class);
        cachedRepository = new CachedTaxRepository(delegate, cacheManager);
    }

    @Test
    void getActiveTaxProfile_shouldUseCacheOnHit() {
        TaxProfile profile = mock(TaxProfile.class);
        when(cacheManager.getOrCompute(eq("tax_rules"), eq("active_profile"), any())).thenReturn(Optional.of(profile));
        
        Optional<TaxProfile> result = cachedRepository.getActiveTaxProfile();
        
        assertTrue(result.isPresent());
        verify(cacheManager).getOrCompute(eq("tax_rules"), eq("active_profile"), any());
    }

    @Test
    void getAllTaxProfiles_shouldUseCacheOnHit() {
        List<TaxProfile> profiles = List.of(mock(TaxProfile.class));
        when(cacheManager.getOrCompute(eq("tax_rules"), eq("all_profiles"), any())).thenReturn(profiles);
        
        List<TaxProfile> result = cachedRepository.getAllTaxProfiles();
        
        assertEquals(profiles, result);
        verify(cacheManager).getOrCompute(eq("tax_rules"), eq("all_profiles"), any());
    }

    @Test
    void createTaxProfile_shouldInvalidateCache() {
        TaxProfile profile = mock(TaxProfile.class);
        when(delegate.createTaxProfile(profile)).thenReturn(1L);
        
        long id = cachedRepository.createTaxProfile(profile);
        
        assertEquals(1L, id);
        verify(delegate).createTaxProfile(profile);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxProfile_shouldInvalidateCacheOnSuccess() {
        TaxProfile profile = mock(TaxProfile.class);
        when(delegate.updateTaxProfile(1L, profile)).thenReturn(1);
        
        int result = cachedRepository.updateTaxProfile(1L, profile);
        
        assertEquals(1, result);
        verify(delegate).updateTaxProfile(1L, profile);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxProfile_shouldNotInvalidateCacheOnFailure() {
        TaxProfile profile = mock(TaxProfile.class);
        when(delegate.updateTaxProfile(1L, profile)).thenReturn(0);
        
        int result = cachedRepository.updateTaxProfile(1L, profile);
        
        assertEquals(0, result);
        verify(delegate).updateTaxProfile(1L, profile);
        verify(cacheManager, never()).invalidateAll(any());
    }

    @Test
    void deleteTaxProfile_shouldInvalidateCacheOnSuccess() {
        when(delegate.deleteTaxProfile(1L)).thenReturn(1);
        
        int result = cachedRepository.deleteTaxProfile(1L);
        
        assertEquals(1, result);
        verify(delegate).deleteTaxProfile(1L);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void deleteTaxProfile_shouldNotInvalidateCacheOnFailure() {
        when(delegate.deleteTaxProfile(1L)).thenReturn(0);
        
        int result = cachedRepository.deleteTaxProfile(1L);
        
        assertEquals(0, result);
        verify(delegate).deleteTaxProfile(1L);
        verify(cacheManager, never()).invalidateAll(any());
    }

    @Test
    void getAllTaxCategories_shouldUseCache() {
        List<TaxCategory> categories = List.of(mock(TaxCategory.class));
        when(cacheManager.getOrCompute(eq("categories"), eq("tax_categories"), any())).thenReturn(categories);
        
        List<TaxCategory> result = cachedRepository.getAllTaxCategories();
        
        assertEquals(categories, result);
        verify(cacheManager).getOrCompute(eq("categories"), eq("tax_categories"), any());
    }

    @Test
    void createTaxCategory_shouldInvalidateCache() {
        when(delegate.createTaxCategory("Test", "Description")).thenReturn(1L);
        
        long id = cachedRepository.createTaxCategory("Test", "Description");
        
        assertEquals(1L, id);
        verify(delegate).createTaxCategory("Test", "Description");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxCategory_shouldInvalidateCacheOnSuccess() {
        when(delegate.updateTaxCategory(1L, "Test", "Description")).thenReturn(1);
        
        int result = cachedRepository.updateTaxCategory(1L, "Test", "Description");
        
        assertEquals(1, result);
        verify(delegate).updateTaxCategory(1L, "Test", "Description");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxCategory_shouldNotInvalidateCacheOnFailure() {
        when(delegate.updateTaxCategory(1L, "Test", "Description")).thenReturn(0);
        
        int result = cachedRepository.updateTaxCategory(1L, "Test", "Description");
        
        assertEquals(0, result);
        verify(delegate).updateTaxCategory(1L, "Test", "Description");
        verify(cacheManager, never()).invalidateAll(any());
    }

    @Test
    void deleteTaxCategory_shouldInvalidateCacheOnSuccess() {
        when(delegate.deleteTaxCategory(1L)).thenReturn(1);
        
        int result = cachedRepository.deleteTaxCategory(1L);
        
        assertEquals(1, result);
        verify(delegate).deleteTaxCategory(1L);
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void deleteTaxCategory_shouldNotInvalidateCacheOnFailure() {
        when(delegate.deleteTaxCategory(1L)).thenReturn(0);
        
        int result = cachedRepository.deleteTaxCategory(1L);
        
        assertEquals(0, result);
        verify(delegate).deleteTaxCategory(1L);
        verify(cacheManager, never()).invalidateAll(any());
    }

    @Test
    void getTaxRulesByProfileId_shouldUseCache() {
        List<TaxRule> rules = List.of(mock(TaxRule.class));
        when(cacheManager.getOrCompute(eq("tax_rules"), eq("profile_1"), any())).thenReturn(rules);
        
        List<TaxRule> result = cachedRepository.getTaxRulesByProfileId(1L);
        
        assertEquals(rules, result);
        verify(cacheManager).getOrCompute(eq("tax_rules"), eq("profile_1"), any());
    }

    @Test
    void createTaxRule_shouldInvalidateCache() {
        TaxRule rule = mock(TaxRule.class);
        when(delegate.createTaxRule(rule)).thenReturn(1L);
        
        long id = cachedRepository.createTaxRule(rule);
        
        assertEquals(1L, id);
        verify(delegate).createTaxRule(rule);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxRule_shouldInvalidateCacheOnSuccess() {
        TaxRule rule = mock(TaxRule.class);
        when(delegate.updateTaxRule(1L, rule)).thenReturn(1);
        
        int result = cachedRepository.updateTaxRule(1L, rule);
        
        assertEquals(1, result);
        verify(delegate).updateTaxRule(1L, rule);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void updateTaxRule_shouldNotInvalidateCacheOnFailure() {
        TaxRule rule = mock(TaxRule.class);
        when(delegate.updateTaxRule(1L, rule)).thenReturn(0);
        
        int result = cachedRepository.updateTaxRule(1L, rule);
        
        assertEquals(0, result);
        verify(delegate).updateTaxRule(1L, rule);
        verify(cacheManager, never()).invalidateAll(any());
    }

    @Test
    void deleteTaxRule_shouldInvalidateCacheOnSuccess() {
        when(delegate.deleteTaxRule(1L)).thenReturn(1);
        
        int result = cachedRepository.deleteTaxRule(1L);
        
        assertEquals(1, result);
        verify(delegate).deleteTaxRule(1L);
        verify(cacheManager).invalidateAll("tax_rules");
        verify(cacheManager).invalidateAll("categories");
    }

    @Test
    void deleteTaxRule_shouldNotInvalidateCacheOnFailure() {
        when(delegate.deleteTaxRule(1L)).thenReturn(0);
        
        int result = cachedRepository.deleteTaxRule(1L);
        
        assertEquals(0, result);
        verify(delegate).deleteTaxRule(1L);
        verify(cacheManager, never()).invalidateAll(any());
    }
}

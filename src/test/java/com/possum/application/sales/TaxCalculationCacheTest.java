package com.possum.application.sales;

import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.application.sales.dto.TaxableItem;
import com.possum.domain.model.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaxCalculationCacheTest {
    
    private TaxCalculationCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new TaxCalculationCache(5000, 100);
    }
    
    @Test
    void testCacheMiss() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = cache.get(invoice, null);
        
        assertNull(result);
        assertEquals(1, cache.getStats().misses());
    }
    
    @Test
    void testCacheHit() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult expected = new TaxCalculationResult(
                List.of(item),
                new BigDecimal("10.00"),
                new BigDecimal("110.00")
        );
        
        cache.put(invoice, null, expected);
        TaxCalculationResult actual = cache.get(invoice, null);
        
        assertNotNull(actual);
        assertEquals(expected.totalTax(), actual.totalTax());
        assertEquals(1, cache.getStats().hits());
    }
    
    @Test
    void testCacheWithCustomer() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        Customer customer = new Customer(1L, "Test", null, null, null, "retail", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
        
        TaxCalculationResult expected = new TaxCalculationResult(
                List.of(item),
                new BigDecimal("10.00"),
                new BigDecimal("110.00")
        );
        
        cache.put(invoice, customer, expected);
        TaxCalculationResult actual = cache.get(invoice, customer);
        
        assertNotNull(actual);
        assertEquals(expected.totalTax(), actual.totalTax());
    }
    
    @Test
    void testCacheExemptCustomer() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        Customer exemptCustomer = new Customer(1L, "NGO", null, null, null, "ngo", true,
                LocalDateTime.now(), LocalDateTime.now(), null);
        Customer normalCustomer = new Customer(1L, "NGO", null, null, null, "ngo", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
        
        TaxCalculationResult exemptResult = new TaxCalculationResult(
                List.of(item), BigDecimal.ZERO, new BigDecimal("100.00"));
        
        cache.put(invoice, exemptCustomer, exemptResult);
        
        TaxCalculationResult result1 = cache.get(invoice, exemptCustomer);
        assertNotNull(result1);
        
        TaxCalculationResult result2 = cache.get(invoice, normalCustomer);
        assertNull(result2);
    }
    
    @Test
    void testCacheClear() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = new TaxCalculationResult(
                List.of(item), new BigDecimal("10.00"), new BigDecimal("110.00"));
        
        cache.put(invoice, null, result);
        assertEquals(1, cache.getStats().size());
        
        cache.clear();
        assertEquals(0, cache.getStats().size());
    }
    
    @Test
    void testCacheStats() {
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = new TaxCalculationResult(
                List.of(item), new BigDecimal("10.00"), new BigDecimal("110.00"));
        
        cache.get(invoice, null);
        cache.put(invoice, null, result);
        cache.get(invoice, null);
        cache.get(invoice, null);
        
        TaxCalculationCache.CacheStats stats = cache.getStats();
        
        assertEquals(3, stats.totalRequests());
        assertEquals(2, stats.hits());
        assertEquals(1, stats.misses());
        assertTrue(stats.hitRate() > 0);
    }
    
    @Test
    void testCacheExpiry() throws InterruptedException {
        cache = new TaxCalculationCache(100, 100);
        
        TaxableItem item = new TaxableItem("Product", "Variant", new BigDecimal("100"), 1, null, 1L, 1L);
        TaxableInvoice invoice = new TaxableInvoice(List.of(item));
        
        TaxCalculationResult result = new TaxCalculationResult(
                List.of(item), new BigDecimal("10.00"), new BigDecimal("110.00"));
        
        cache.put(invoice, null, result);
        
        Thread.sleep(150);
        
        TaxCalculationResult expired = cache.get(invoice, null);
        assertNull(expired);
    }
}

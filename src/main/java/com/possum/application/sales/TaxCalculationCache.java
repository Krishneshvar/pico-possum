package com.possum.application.sales;

import com.possum.application.sales.dto.TaxCalculationResult;
import com.possum.application.sales.dto.TaxableInvoice;
import com.possum.domain.model.Customer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache for tax calculations to avoid recalculating identical invoices.
 * Uses invoice hash as key for fast lookups.
 */
public class TaxCalculationCache {
    
    private final ConcurrentHashMap<String, CachedCalculation> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSize;
    
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    
    private static final long DEFAULT_TTL_MILLIS = 5 * 60 * 1000; // 5 minutes
    private static final int DEFAULT_MAX_SIZE = 1000;
    
    public TaxCalculationCache() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_MAX_SIZE);
    }
    
    public TaxCalculationCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
    }
    
    /**
     * Gets cached calculation if available and not expired.
     */
    public TaxCalculationResult get(TaxableInvoice invoice, Customer customer) {
        String key = generateKey(invoice, customer);
        CachedCalculation cached = cache.get(key);
        
        if (cached == null) {
            misses.incrementAndGet();
            return null;
        }
        
        if (isExpired(cached)) {
            cache.remove(key);
            misses.incrementAndGet();
            return null;
        }
        
        hits.incrementAndGet();
        return cached.result;
    }
    
    /**
     * Stores calculation result in cache.
     */
    public void put(TaxableInvoice invoice, Customer customer, TaxCalculationResult result) {
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        
        String key = generateKey(invoice, customer);
        cache.put(key, new CachedCalculation(result, System.currentTimeMillis()));
    }
    
    /**
     * Clears all cached calculations.
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Removes expired entries from cache.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > ttlMillis);
    }
    
    /**
     * Gets cache statistics.
     */
    public CacheStats getStats() {
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests > 0 ? (double) hits.get() / totalRequests * 100 : 0.0;
        
        return new CacheStats(
                hits.get(),
                misses.get(),
                cache.size(),
                hitRate
        );
    }
    
    private String generateKey(TaxableInvoice invoice, Customer customer) {
        StringBuilder key = new StringBuilder();
        
        // Include customer tax exemption status
        if (customer != null) {
            key.append("C").append(customer.id());
            if (Boolean.TRUE.equals(customer.isTaxExempt())) {
                key.append("_EXEMPT");
            }
            if (customer.customerType() != null) {
                key.append("_").append(customer.customerType());
            }
        } else {
            key.append("NO_CUSTOMER");
        }
        
        // Include invoice items
        key.append("_ITEMS:");
        for (var item : invoice.items()) {
            key.append(item.getProductId())
               .append("_").append(item.getVariantId())
               .append("_").append(item.getPrice())
               .append("_").append(item.getQuantity())
               .append("_").append(item.getTaxCategoryId())
               .append("|");
        }
        
        return key.toString();
    }
    
    private boolean isExpired(CachedCalculation cached) {
        return System.currentTimeMillis() - cached.timestamp > ttlMillis;
    }
    
    private void evictOldest() {
        // Simple LRU: remove oldest entry
        cache.entrySet().stream()
                .min((e1, e2) -> Long.compare(e1.getValue().timestamp, e2.getValue().timestamp))
                .ifPresent(entry -> cache.remove(entry.getKey()));
    }
    
    private record CachedCalculation(TaxCalculationResult result, long timestamp) {}
    
    public record CacheStats(long hits, long misses, int size, double hitRate) {
        public long totalRequests() {
            return hits + misses;
        }
    }
}

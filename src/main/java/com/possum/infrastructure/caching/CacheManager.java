package com.possum.infrastructure.caching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CacheManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);
    
    private final Map<String, SimpleCache<String, Object>> caches = new ConcurrentHashMap<>();
    
    public CacheManager() {
        initializeDefaultCaches();
    }
    
    private void initializeDefaultCaches() {
        createCache("tax_rules", 1000, Duration.ofHours(1));
        createCache("roles", 100, Duration.ofHours(2));
        createCache("permissions", 500, Duration.ofHours(2));
        createCache("user_permissions", 1000, Duration.ofMinutes(30));
        createCache("products", 5000, Duration.ofMinutes(15));
        createCache("categories", 200, Duration.ofHours(1));
        createCache("payment_methods", 50, Duration.ofHours(4));
        
        LOGGER.info("Cache manager initialized with {} caches", caches.size());
    }
    
    public void createCache(String cacheName, int maxSize, Duration ttl) {
        SimpleCache<String, Object> cache = new SimpleCache<>(maxSize, ttl);
        caches.put(cacheName, cache);
        LOGGER.debug("Created cache: name={}, maxSize={}, ttl={}", cacheName, maxSize, ttl);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        if (cache == null) {
            LOGGER.warn("Cache not found: {}", cacheName);
            return null;
        }
        return (T) cache.get(key);
    }
    
    public <T> void put(String cacheName, String key, T value) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        if (cache == null) {
            LOGGER.warn("Cache not found: {}", cacheName);
            return;
        }
        cache.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String cacheName, String key, java.util.function.Supplier<T> supplier) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        if (cache == null) {
            LOGGER.warn("Cache not found: {}, computing value directly", cacheName);
            return supplier.get();
        }
        
        T value = (T) cache.get(key);
        if (value == null) {
            value = supplier.get();
            if (value != null) {
                cache.put(key, value);
            }
        }
        return value;
    }
    
    public void invalidate(String cacheName, String key) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
            LOGGER.debug("Invalidated cache entry: cache={}, key={}", cacheName, key);
        }
    }
    
    public void invalidateAll(String cacheName) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
            LOGGER.info("Invalidated all entries in cache: {}", cacheName);
        }
    }
    
    public void invalidateAllCaches() {
        caches.values().forEach(SimpleCache::invalidateAll);
        LOGGER.info("Invalidated all caches");
    }
    
    public CacheStats getStats(String cacheName) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        return cache != null ? cache.getStats() : null;
    }
    
    public long getSize(String cacheName) {
        SimpleCache<String, Object> cache = caches.get(cacheName);
        return cache != null ? cache.size() : 0;
    }
    
    public void cleanup() {
        caches.values().forEach(SimpleCache::cleanup);
        LOGGER.debug("Performed cleanup on all caches");
    }
    
    private static class SimpleCache<K, V> {
        private final Map<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
        private final int maxSize;
        private final Duration ttl;
        private long hits = 0;
        private long misses = 0;
        
        SimpleCache(int maxSize, Duration ttl) {
            this.maxSize = maxSize;
            this.ttl = ttl;
        }
        
        V get(K key) {
            CacheEntry<V> entry = cache.get(key);
            if (entry == null) {
                misses++;
                return null;
            }
            
            if (entry.isExpired()) {
                cache.remove(key);
                misses++;
                return null;
            }
            
            hits++;
            return entry.value;
        }
        
        void put(K key, V value) {
            if (cache.size() >= maxSize) {
                evictOldest();
            }
            cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
        }
        
        void invalidate(K key) {
            cache.remove(key);
        }
        
        void invalidateAll() {
            cache.clear();
        }
        
        int size() {
            return cache.size();
        }
        
        void cleanup() {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
        
        CacheStats getStats() {
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0.0;
            return new CacheStats(hits, misses, hitRate, cache.size());
        }
        
        private void evictOldest() {
            cache.entrySet().stream()
                .min((e1, e2) -> e1.getValue().expiresAt.compareTo(e2.getValue().expiresAt))
                .ifPresent(entry -> cache.remove(entry.getKey()));
        }
    }
    
    private static class CacheEntry<V> {
        final V value;
        final Instant expiresAt;
        
        CacheEntry(V value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    public record CacheStats(long hits, long misses, double hitRate, int size) {}
}

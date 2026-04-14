package com.possum.infrastructure.caching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    void put_shouldStoreNewEntry() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        
        String value = cacheManager.get("test_cache", "key1");
        assertEquals("value1", value);
    }

    @Test
    void put_shouldUpdateExistingEntry() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        cacheManager.put("test_cache", "key1", "value2");
        
        String value = cacheManager.get("test_cache", "key1");
        assertEquals("value2", value);
    }

    @Test
    void put_shouldEvictOldestWhenMaxSizeReached() {
        cacheManager.createCache("small_cache", 2, Duration.ofMinutes(5));
        
        cacheManager.put("small_cache", "key1", "value1");
        cacheManager.put("small_cache", "key2", "value2");
        cacheManager.put("small_cache", "key3", "value3");
        
        assertEquals(2, cacheManager.getSize("small_cache"));
    }

    @Test
    void get_shouldReturnCacheHit() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        
        String value = cacheManager.get("test_cache", "key1");
        
        assertEquals("value1", value);
        CacheManager.CacheStats stats = cacheManager.getStats("test_cache");
        assertTrue(stats.hits() > 0);
    }

    @Test
    void get_shouldReturnNullOnCacheMiss() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        
        String value = cacheManager.get("test_cache", "nonexistent");
        
        assertNull(value);
        CacheManager.CacheStats stats = cacheManager.getStats("test_cache");
        assertTrue(stats.misses() > 0);
    }

    @Test
    void get_shouldReturnNullForExpiredEntry() throws InterruptedException {
        cacheManager.createCache("short_ttl_cache", 10, Duration.ofMillis(50));
        cacheManager.put("short_ttl_cache", "key1", "value1");
        
        Thread.sleep(100);
        
        String value = cacheManager.get("short_ttl_cache", "key1");
        assertNull(value);
    }

    @Test
    void invalidate_shouldRemoveSingleKey() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        cacheManager.put("test_cache", "key2", "value2");
        
        cacheManager.invalidate("test_cache", "key1");
        
        assertNull(cacheManager.get("test_cache", "key1"));
        assertEquals("value2", cacheManager.get("test_cache", "key2"));
    }

    @Test
    void invalidateAll_shouldClearCache() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        cacheManager.put("test_cache", "key2", "value2");
        
        cacheManager.invalidateAll("test_cache");
        
        assertNull(cacheManager.get("test_cache", "key1"));
        assertNull(cacheManager.get("test_cache", "key2"));
        assertEquals(0, cacheManager.getSize("test_cache"));
    }

    @Test
    void invalidateAllCaches_shouldClearAllCaches() {
        cacheManager.createCache("cache1", 10, Duration.ofMinutes(5));
        cacheManager.createCache("cache2", 10, Duration.ofMinutes(5));
        cacheManager.put("cache1", "key1", "value1");
        cacheManager.put("cache2", "key2", "value2");
        
        cacheManager.invalidateAllCaches();
        
        assertNull(cacheManager.get("cache1", "key1"));
        assertNull(cacheManager.get("cache2", "key2"));
    }

    @Test
    void getStats_shouldReturnCorrectHitRate() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        
        cacheManager.get("test_cache", "key1");
        cacheManager.get("test_cache", "key1");
        cacheManager.get("test_cache", "key2");
        
        CacheManager.CacheStats stats = cacheManager.getStats("test_cache");
        assertEquals(2, stats.hits());
        assertEquals(1, stats.misses());
        assertEquals(2.0 / 3.0, stats.hitRate(), 0.01);
    }

    @Test
    void getStats_shouldReturnNullForNonexistentCache() {
        CacheManager.CacheStats stats = cacheManager.getStats("nonexistent");
        assertNull(stats);
    }

    @Test
    void getSize_shouldReturnCorrectSize() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "value1");
        cacheManager.put("test_cache", "key2", "value2");
        
        assertEquals(2, cacheManager.getSize("test_cache"));
    }

    @Test
    void getSize_shouldReturnZeroForNonexistentCache() {
        assertEquals(0, cacheManager.getSize("nonexistent"));
    }

    @Test
    void cleanup_shouldRemoveExpiredEntries() throws InterruptedException {
        cacheManager.createCache("short_ttl_cache", 10, Duration.ofMillis(50));
        cacheManager.put("short_ttl_cache", "key1", "value1");
        cacheManager.put("short_ttl_cache", "key2", "value2");
        
        Thread.sleep(100);
        cacheManager.cleanup();
        
        assertEquals(0, cacheManager.getSize("short_ttl_cache"));
    }

    @Test
    void getOrCompute_shouldReturnCachedValue() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        cacheManager.put("test_cache", "key1", "cached");
        
        String value = cacheManager.getOrCompute("test_cache", "key1", () -> "computed");
        
        assertEquals("cached", value);
    }

    @Test
    void getOrCompute_shouldComputeAndCacheOnMiss() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        
        String value = cacheManager.getOrCompute("test_cache", "key1", () -> "computed");
        
        assertEquals("computed", value);
        assertEquals("computed", cacheManager.get("test_cache", "key1"));
    }

    @Test
    void getOrCompute_shouldNotCacheNullValues() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        
        String value = cacheManager.getOrCompute("test_cache", "key1", () -> null);
        
        assertNull(value);
        assertEquals(0, cacheManager.getSize("test_cache"));
    }

    @Test
    void getOrCompute_shouldComputeDirectlyForNonexistentCache() {
        String value = cacheManager.getOrCompute("nonexistent", "key1", () -> "computed");
        
        assertEquals("computed", value);
    }

    @Test
    void createCache_shouldCreateNewCache() {
        cacheManager.createCache("new_cache", 100, Duration.ofHours(1));
        
        cacheManager.put("new_cache", "key1", "value1");
        assertEquals("value1", cacheManager.get("new_cache", "key1"));
    }

    @Test
    void defaultCaches_shouldBeInitialized() {
        assertNotNull(cacheManager.getStats("tax_rules"));
        assertNotNull(cacheManager.getStats("roles"));
        assertNotNull(cacheManager.getStats("permissions"));
        assertNotNull(cacheManager.getStats("user_permissions"));
        assertNotNull(cacheManager.getStats("products"));
        assertNotNull(cacheManager.getStats("categories"));
        assertNotNull(cacheManager.getStats("payment_methods"));
    }
}

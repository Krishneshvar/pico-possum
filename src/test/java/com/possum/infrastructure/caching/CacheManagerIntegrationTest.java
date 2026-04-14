package com.possum.infrastructure.caching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerIntegrationTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new CacheManager();
    }

    @Test
    void cacheWithDatabaseFallback_shouldWorkCorrectly() {
        cacheManager.createCache("test_cache", 10, Duration.ofMinutes(5));
        AtomicInteger dbCallCount = new AtomicInteger(0);
        
        String value1 = cacheManager.getOrCompute("test_cache", "key1", () -> {
            dbCallCount.incrementAndGet();
            return "value_from_db";
        });
        
        String value2 = cacheManager.getOrCompute("test_cache", "key1", () -> {
            dbCallCount.incrementAndGet();
            return "value_from_db";
        });
        
        assertEquals("value_from_db", value1);
        assertEquals("value_from_db", value2);
        assertEquals(1, dbCallCount.get());
    }

    @Test
    void cacheInvalidationPropagation_shouldClearRelatedCaches() {
        cacheManager.createCache("cache1", 10, Duration.ofMinutes(5));
        cacheManager.createCache("cache2", 10, Duration.ofMinutes(5));
        
        cacheManager.put("cache1", "key1", "value1");
        cacheManager.put("cache2", "key2", "value2");
        
        cacheManager.invalidateAll("cache1");
        
        assertNull(cacheManager.get("cache1", "key1"));
        assertEquals("value2", cacheManager.get("cache2", "key2"));
    }

    @Test
    void cachePerformanceImprovement_shouldReduceComputationTime() {
        cacheManager.createCache("perf_cache", 100, Duration.ofMinutes(5));
        
        long startUncached = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            cacheManager.getOrCompute("perf_cache", "key" + i, () -> {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "value";
            });
        }
        long uncachedTime = System.nanoTime() - startUncached;
        
        long startCached = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            cacheManager.get("perf_cache", "key" + i);
        }
        long cachedTime = System.nanoTime() - startCached;
        
        assertTrue(cachedTime < uncachedTime / 10);
    }

    @Test
    void cacheMemoryManagement_shouldNotGrowIndefinitely() {
        cacheManager.createCache("bounded_cache", 10, Duration.ofMinutes(5));
        
        for (int i = 0; i < 100; i++) {
            cacheManager.put("bounded_cache", "key" + i, "value" + i);
        }
        
        assertTrue(cacheManager.getSize("bounded_cache") <= 10);
    }

    @Test
    void concurrentCacheAccess_shouldBeThreadSafe() throws InterruptedException {
        cacheManager.createCache("concurrent_cache", 100, Duration.ofMinutes(5));
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "key" + (i % 10);
                        cacheManager.put("concurrent_cache", key, "value" + threadId);
                        cacheManager.get("concurrent_cache", key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void cacheExpiry_shouldRemoveExpiredEntries() throws InterruptedException {
        cacheManager.createCache("expiry_cache", 10, Duration.ofMillis(100));
        
        cacheManager.put("expiry_cache", "key1", "value1");
        assertEquals("value1", cacheManager.get("expiry_cache", "key1"));
        
        Thread.sleep(150);
        
        assertNull(cacheManager.get("expiry_cache", "key1"));
    }

    @Test
    void multiLevelCacheCoordination_shouldWorkCorrectly() {
        cacheManager.createCache("l1_cache", 5, Duration.ofMinutes(1));
        cacheManager.createCache("l2_cache", 10, Duration.ofMinutes(5));
        
        cacheManager.put("l1_cache", "key1", "value1");
        cacheManager.put("l2_cache", "key1", "value1");
        
        cacheManager.invalidate("l1_cache", "key1");
        
        assertNull(cacheManager.get("l1_cache", "key1"));
        assertEquals("value1", cacheManager.get("l2_cache", "key1"));
    }

    @Test
    void cacheStats_shouldTrackAccurately() {
        cacheManager.createCache("stats_cache", 10, Duration.ofMinutes(5));
        
        cacheManager.put("stats_cache", "key1", "value1");
        
        for (int i = 0; i < 10; i++) {
            cacheManager.get("stats_cache", "key1");
        }
        
        for (int i = 0; i < 5; i++) {
            cacheManager.get("stats_cache", "nonexistent");
        }
        
        CacheManager.CacheStats stats = cacheManager.getStats("stats_cache");
        assertEquals(10, stats.hits());
        assertEquals(5, stats.misses());
        assertEquals(10.0 / 15.0, stats.hitRate(), 0.01);
    }
}

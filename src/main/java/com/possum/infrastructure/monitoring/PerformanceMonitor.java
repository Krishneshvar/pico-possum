package com.possum.infrastructure.monitoring;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance monitoring service for tracking operation metrics.
 * Provides insights into system performance and bottlenecks.
 */
public class PerformanceMonitor {
    
    private final ConcurrentHashMap<String, OperationMetrics> metrics = new ConcurrentHashMap<>();
    
    /**
     * Records the execution time of an operation.
     */
    public void recordOperation(String operationName, long durationMillis) {
        metrics.computeIfAbsent(operationName, k -> new OperationMetrics())
               .record(durationMillis);
    }
    
    /**
     * Records a successful operation.
     */
    public void recordSuccess(String operationName) {
        metrics.computeIfAbsent(operationName, k -> new OperationMetrics())
               .recordSuccess();
    }
    
    /**
     * Records a failed operation.
     */
    public void recordFailure(String operationName) {
        metrics.computeIfAbsent(operationName, k -> new OperationMetrics())
               .recordFailure();
    }
    
    /**
     * Gets metrics for a specific operation.
     */
    public OperationStats getStats(String operationName) {
        OperationMetrics m = metrics.get(operationName);
        return m != null ? m.getStats() : new OperationStats(operationName, 0, 0, 0, 0, 0, 0, 0);
    }
    
    /**
     * Gets metrics for all operations.
     */
    public java.util.Map<String, OperationStats> getAllStats() {
        java.util.Map<String, OperationStats> allStats = new java.util.HashMap<>();
        metrics.forEach((name, m) -> allStats.put(name, m.getStats()));
        return allStats;
    }
    
    /**
     * Resets all metrics.
     */
    public void reset() {
        metrics.clear();
    }
    
    /**
     * Resets metrics for a specific operation.
     */
    public void reset(String operationName) {
        metrics.remove(operationName);
    }
    
    private static class OperationMetrics {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalDuration = new LongAdder();
        private final AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDuration = new AtomicLong(0);
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        
        void record(long durationMillis) {
            count.increment();
            totalDuration.add(durationMillis);
            
            minDuration.updateAndGet(current -> Math.min(current, durationMillis));
            maxDuration.updateAndGet(current -> Math.max(current, durationMillis));
        }
        
        void recordSuccess() {
            successCount.increment();
        }
        
        void recordFailure() {
            failureCount.increment();
        }
        
        OperationStats getStats() {
            long cnt = count.sum();
            long total = totalDuration.sum();
            long min = minDuration.get();
            long max = maxDuration.get();
            long success = successCount.sum();
            long failure = failureCount.sum();
            
            double avg = cnt > 0 ? (double) total / cnt : 0.0;
            
            return new OperationStats(
                    null,
                    cnt,
                    total,
                    min == Long.MAX_VALUE ? 0 : min,
                    max,
                    avg,
                    success,
                    failure
            );
        }
    }
    
    public record OperationStats(
            String operationName,
            long count,
            long totalDurationMillis,
            long minDurationMillis,
            long maxDurationMillis,
            double avgDurationMillis,
            long successCount,
            long failureCount
    ) {
        public double successRate() {
            long total = successCount + failureCount;
            return total > 0 ? (double) successCount / total * 100 : 100.0;
        }
        
        public double throughputPerSecond() {
            return totalDurationMillis > 0 ? (double) count / (totalDurationMillis / 1000.0) : 0.0;
        }
    }
}

package com.possum.infrastructure.async;

import com.possum.domain.model.AuditLog;
import com.possum.infrastructure.logging.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous wrapper for AuditLogger that queues audit events for background processing.
 * Improves application performance by not blocking on audit log writes.
 */
public final class AsyncAuditLogger {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncAuditLogger.class);
    
    private final AuditLogger auditLogger;
    private final ExecutorService executor;
    private final BlockingQueue<AuditTask> queue;
    private final AtomicLong queuedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    
    private static final int DEFAULT_QUEUE_SIZE = 10000;
    private static final int DEFAULT_THREAD_POOL_SIZE = 2;
    
    public AsyncAuditLogger(AuditLogger auditLogger) {
        this(auditLogger, DEFAULT_QUEUE_SIZE, DEFAULT_THREAD_POOL_SIZE);
    }
    
    public AsyncAuditLogger(AuditLogger auditLogger, int queueSize, int threadPoolSize) {
        this.auditLogger = auditLogger;
        this.queue = new LinkedBlockingQueue<>(queueSize);
        this.executor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "AsyncAuditLogger-Worker");
            t.setDaemon(true);
            return t;
        });
        
        startWorkers(threadPoolSize);
        LOGGER.info("AsyncAuditLogger initialized with queue size {} and {} workers", queueSize, threadPoolSize);
    }
    
    private void startWorkers(int count) {
        for (int i = 0; i < count; i++) {
            executor.submit(this::processQueue);
        }
    }
    
    private void processQueue() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AuditTask task = queue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    task.execute(auditLogger);
                    processedCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failedCount.incrementAndGet();
                LOGGER.error("Failed to process audit task", e);
            }
        }
    }
    
    public void logAuthentication(Long userId, String action, boolean success, 
                                  String ipAddress, String userAgent, String details) {
        enqueue(new AuthenticationTask(userId, action, success, ipAddress, userAgent, details));
    }
    
    public void logAuthorization(Long userId, String permission, boolean granted, 
                                String ipAddress, String details) {
        enqueue(new AuthorizationTask(userId, permission, granted, ipAddress, details));
    }
    
    public void logDataModification(Long userId, String action, String tableName, Long rowId,
                                   String oldData, String newData) {
        enqueue(new DataModificationTask(userId, action, tableName, rowId, oldData, newData));
    }
    
    public void logSecurityEvent(Long userId, String action, String details, 
                                String ipAddress, String severity) {
        enqueue(new SecurityEventTask(userId, action, details, ipAddress, severity));
    }
    
    public void logCriticalEvent(Long userId, String action, String details, String ipAddress) {
        // Critical events are logged synchronously to ensure they're persisted immediately
        try {
            auditLogger.logCriticalEvent(userId, action, details, ipAddress);
        } catch (Exception e) {
            LOGGER.error("Failed to log critical event synchronously", e);
        }
    }
    
    private void enqueue(AuditTask task) {
        try {
            if (!queue.offer(task, 100, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Audit queue full, dropping event: {}", task.getClass().getSimpleName());
                failedCount.incrementAndGet();
            } else {
                queuedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while enqueuing audit task", e);
            failedCount.incrementAndGet();
        }
    }
    
    public void shutdown() {
        LOGGER.info("Shutting down AsyncAuditLogger...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                LOGGER.warn("AsyncAuditLogger forced shutdown after timeout");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AsyncAuditLogger shutdown complete. Processed: {}, Failed: {}, Remaining: {}",
                processedCount.get(), failedCount.get(), queue.size());
    }
    
    public AsyncAuditStats getStats() {
        return new AsyncAuditStats(
                queuedCount.get(),
                processedCount.get(),
                failedCount.get(),
                queue.size()
        );
    }
    
    // Task interfaces
    private interface AuditTask {
        void execute(AuditLogger logger);
    }
    
    private record AuthenticationTask(Long userId, String action, boolean success,
                                     String ipAddress, String userAgent, String details) implements AuditTask {
        @Override
        public void execute(AuditLogger logger) {
            logger.logAuthentication(userId, action, success, ipAddress, userAgent, details);
        }
    }
    
    private record AuthorizationTask(Long userId, String permission, boolean granted,
                                    String ipAddress, String details) implements AuditTask {
        @Override
        public void execute(AuditLogger logger) {
            logger.logAuthorization(userId, permission, granted, ipAddress, details);
        }
    }
    
    private record DataModificationTask(Long userId, String action, String tableName, Long rowId,
                                       String oldData, String newData) implements AuditTask {
        @Override
        public void execute(AuditLogger logger) {
            logger.logDataModification(userId, action, tableName, rowId, oldData, newData);
        }
    }
    
    private record SecurityEventTask(Long userId, String action, String details,
                                    String ipAddress, String severity) implements AuditTask {
        @Override
        public void execute(AuditLogger logger) {
            logger.logSecurityEvent(userId, action, details, ipAddress, severity);
        }
    }
    
    public record AsyncAuditStats(long queued, long processed, long failed, int queueSize) {
        public double successRate() {
            long total = processed + failed;
            return total > 0 ? (double) processed / total * 100 : 100.0;
        }
    }
}

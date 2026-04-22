package com.picopossum.infrastructure.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centrally manages application-wide background tasks.
 * Follows industry-level standards for resource management and thread naming.
 */
public final class AppExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppExecutor.class);
    
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    public AppExecutor() {
        this.backgroundExecutor = createExecutor("bg-task-");
        this.scheduledExecutor = createScheduledExecutor("sched-task-");
    }

    private ExecutorService createExecutor(String namePrefix) {
        return new ThreadPoolExecutor(2, 8, 
            60L, TimeUnit.SECONDS, 
            new LinkedBlockingQueue<>(100),
            new NamedThreadFactory(namePrefix),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private ScheduledExecutorService createScheduledExecutor(String namePrefix) {
        return Executors.newScheduledThreadPool(2, new NamedThreadFactory(namePrefix));
    }

    public void execute(Runnable task) {
        backgroundExecutor.execute(task);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return backgroundExecutor.submit(task);
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduledExecutor.schedule(task, delay, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutor.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    public void shutdown() {
        LOGGER.info("Shutting down AppExecutor...");
        shutdownAndAwaitTermination(backgroundExecutor, "background");
        shutdownAndAwaitTermination(scheduledExecutor, "scheduled");
    }

    private void shutdownAndAwaitTermination(ExecutorService pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                    LOGGER.error("{} pool did not terminate", name);
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}

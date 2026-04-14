package com.possum.application.auth;

import com.possum.domain.repositories.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SessionCleanupScheduler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCleanupScheduler.class);
    private static final long CLEANUP_INTERVAL_MINUTES = 15;
    
    private final SessionRepository sessionRepository;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    public SessionCleanupScheduler(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "session-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    public synchronized void start() {
        if (running) {
            LOGGER.warn("Session cleanup scheduler is already running");
            return;
        }
        
        LOGGER.info("Starting session cleanup scheduler (interval: {} minutes)", CLEANUP_INTERVAL_MINUTES);
        
        scheduler.scheduleAtFixedRate(
            this::cleanupExpiredSessions,
            CLEANUP_INTERVAL_MINUTES,
            CLEANUP_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        running = true;
    }
    
    public synchronized void stop() {
        if (!running) {
            return;
        }
        
        LOGGER.info("Stopping session cleanup scheduler");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        running = false;
    }
    
    private void cleanupExpiredSessions() {
        try {
            long now = System.currentTimeMillis() / 1000;
            sessionRepository.deleteExpired(now);
            LOGGER.debug("Expired sessions cleaned up successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to cleanup expired sessions", e);
        }
    }
}

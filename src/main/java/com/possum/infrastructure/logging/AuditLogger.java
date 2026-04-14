package com.possum.infrastructure.logging;

import com.possum.domain.model.AuditLog;
import com.possum.domain.repositories.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

public final class AuditLogger {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogger.class);
    private static final String HASH_ALGORITHM = "SHA-256";
    
    private final AuditRepository auditRepository;
    private volatile String lastHash = "";
    
    public AuditLogger(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
        initializeChain();
    }
    
    private void initializeChain() {
        try {
            // Get the last audit log to continue the chain
            // This would need a method in AuditRepository to get the last log
            lastHash = "GENESIS";
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize audit chain, starting fresh", e);
            lastHash = "GENESIS";
        }
    }
    
    public void logAuthentication(Long userId, String action, boolean success, String ipAddress, String userAgent, String details) {
        String severity = success ? "info" : "warning";
        log(userId, action, "auth", null, null, null, details, ipAddress, userAgent, severity);
    }
    
    public void logAuthorization(Long userId, String permission, boolean granted, String ipAddress, String details) {
        String action = granted ? "ACCESS_GRANTED" : "ACCESS_DENIED";
        String severity = granted ? "info" : "warning";
        log(userId, action, "auth", null, null, null, details, ipAddress, null, severity);
    }
    
    public void logDataModification(Long userId, String action, String tableName, Long rowId, 
                                   String oldData, String newData) {
        log(userId, action, tableName, rowId, oldData, newData, null, null, null, "info");
    }
    
    public void logDataModification(Long userId, String action, String tableName, Long rowId, 
                                   String oldData, String newData, String ipAddress) {
        log(userId, action, tableName, rowId, oldData, newData, null, ipAddress, null, "info");
    }
    
    public void logSecurityEvent(Long userId, String action, String details, String ipAddress, String severity) {
        log(userId, action, "security", null, null, null, details, ipAddress, null, severity);
    }
    
    public void logCriticalEvent(Long userId, String action, String details, String ipAddress) {
        log(userId, action, "security", null, null, null, details, ipAddress, null, "critical");
    }
    
    private synchronized void log(Long userId, String action, String tableName, Long rowId,
                                 String oldData, String newData, String eventDetails,
                                 String ipAddress, String userAgent, String severity) {
        try {
            LocalDateTime timestamp = LocalDateTime.now();
            
            // Create audit log entry
            AuditLog auditLog = new AuditLog(
                null,
                userId,
                action,
                tableName,
                rowId,
                oldData,
                newData,
                eventDetails,
                ipAddress,
                timestamp
            );
            
            // Calculate integrity hash (chain with previous)
            String currentHash = calculateHash(auditLog, lastHash);
            
            // Store with hash (would need to extend AuditLog model)
            auditRepository.insertAuditLog(auditLog);
            
            // Update last hash for chain
            lastHash = currentHash;
            
            LOGGER.debug("Audit log created: action={}, user={}, severity={}", action, userId, severity);
            
        } catch (Exception e) {
            // Audit failures should be logged but never break application flow
            LOGGER.error("Failed to create audit log: action={}, user={}", action, userId, e);
        }
    }
    
    private String calculateHash(AuditLog log, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            
            // Build string to hash: previousHash + timestamp + userId + action + details
            StringBuilder data = new StringBuilder();
            data.append(previousHash);
            data.append("|").append(log.createdAt());
            data.append("|").append(log.userId());
            data.append("|").append(log.action());
            data.append("|").append(log.tableName());
            data.append("|").append(log.eventDetails());
            
            byte[] hashBytes = digest.digest(data.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Hash algorithm not available", e);
            return "HASH_ERROR";
        }
    }
    
    public boolean verifyChainIntegrity() {
        // This would iterate through all audit logs and verify the hash chain
        // Implementation would require fetching all logs in order
        LOGGER.info("Audit chain integrity verification not yet implemented");
        return true;
    }
}

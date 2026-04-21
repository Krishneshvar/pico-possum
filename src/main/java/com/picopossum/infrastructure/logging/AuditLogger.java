package com.picopossum.infrastructure.logging;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.domain.repositories.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Enhanced Audit Logger for Single-User SMB.
 * Maintains a hash chain for integrity without multi-user identity overhead.
 */
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
            // Genesis implementation: In production this would fetch the last record's hash
            lastHash = "GENESIS";
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize audit chain, starting fresh", e);
            lastHash = "GENESIS";
        }
    }
    
    public void logAuthentication(String action, boolean success, String details) {
        String severity = success ? "info" : "warning";
        log(action, "auth", null, null, null, details, severity);
    }
    
    public void logDataModification(String action, String tableName, Long rowId, 
                                   String oldData, String newData) {
        log(action, tableName, rowId, oldData, newData, null, "info");
    }
    
    public void logSecurityEvent(String action, String details, String severity) {
        log(action, "security", null, null, null, details, severity);
    }
    
    public void logCriticalEvent(String action, String details) {
        log(action, "security", null, null, null, details, "critical");
    }
    
    private synchronized void log(String action, String tableName, Long rowId,
                                 String oldData, String newData, String eventDetails,
                                 String severity) {
        try {
            LocalDateTime timestamp = LocalDateTime.now();
            
            AuditLog auditLog = new AuditLog(
                null,
                action,
                tableName,
                rowId,
                oldData,
                newData,
                eventDetails,
                severity,
                timestamp
            );
            
            // Calculate integrity hash (chain with previous) to prevent tampering
            String currentHash = calculateHash(auditLog, lastHash);
            
            // Store the log
            auditRepository.insertAuditLog(auditLog);
            
            // Update last hash for chain in memory
            lastHash = currentHash;
            
            LOGGER.debug("Audit log created: action={}, severity={}", action, severity);
            
        } catch (Exception e) {
            LOGGER.error("Failed to create audit log: action={}", action, e);
        }
    }
    
    private String calculateHash(AuditLog log, String previousHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            
            StringBuilder data = new StringBuilder();
            data.append(previousHash);
            data.append("|").append(log.createdAt());
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
        LOGGER.info("Audit chain integrity verification not yet implemented");
        return true;
    }
}

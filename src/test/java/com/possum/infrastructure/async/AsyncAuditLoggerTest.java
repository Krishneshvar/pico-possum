package com.possum.infrastructure.async;

import com.possum.infrastructure.logging.AuditLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncAuditLoggerTest {
    
    private AuditLogger mockAuditLogger;
    private AsyncAuditLogger asyncLogger;
    
    @BeforeEach
    void setUp() {
        mockAuditLogger = mock(AuditLogger.class);
        asyncLogger = new AsyncAuditLogger(mockAuditLogger, 100, 1);
    }
    
    @AfterEach
    void tearDown() {
        asyncLogger.shutdown();
    }
    
    @Test
    void testLogAuthentication() throws InterruptedException {
        asyncLogger.logAuthentication(1L, "LOGIN", true, "127.0.0.1", "Mozilla", "Success");
        
        Thread.sleep(100);
        
        verify(mockAuditLogger, timeout(1000)).logAuthentication(
                eq(1L), eq("LOGIN"), eq(true), eq("127.0.0.1"), eq("Mozilla"), eq("Success"));
    }
    
    @Test
    void testLogAuthorization() throws InterruptedException {
        asyncLogger.logAuthorization(1L, "sales.create", true, "127.0.0.1", "Granted");
        
        Thread.sleep(100);
        
        verify(mockAuditLogger, timeout(1000)).logAuthorization(
                eq(1L), eq("sales.create"), eq(true), eq("127.0.0.1"), eq("Granted"));
    }
    
    @Test
    void testLogDataModification() throws InterruptedException {
        asyncLogger.logDataModification(1L, "CREATE", "sales", 123L, null, "new data");
        
        Thread.sleep(100);
        
        verify(mockAuditLogger, timeout(1000)).logDataModification(
                eq(1L), eq("CREATE"), eq("sales"), eq(123L), isNull(), eq("new data"));
    }
    
    @Test
    void testLogSecurityEvent() throws InterruptedException {
        asyncLogger.logSecurityEvent(1L, "PASSWORD_CHANGE", "Changed password", "127.0.0.1", "info");
        
        Thread.sleep(100);
        
        verify(mockAuditLogger, timeout(1000)).logSecurityEvent(
                eq(1L), eq("PASSWORD_CHANGE"), eq("Changed password"), eq("127.0.0.1"), eq("info"));
    }
    
    @Test
    void testLogCriticalEventSynchronous() {
        asyncLogger.logCriticalEvent(1L, "SECURITY_BREACH", "Critical event", "127.0.0.1");
        
        verify(mockAuditLogger, times(1)).logCriticalEvent(
                eq(1L), eq("SECURITY_BREACH"), eq("Critical event"), eq("127.0.0.1"));
    }
    
    @Test
    void testStats() throws InterruptedException {
        asyncLogger.logDataModification(1L, "CREATE", "sales", 1L, null, "data");
        asyncLogger.logDataModification(1L, "UPDATE", "sales", 2L, "old", "new");
        
        Thread.sleep(200);
        
        AsyncAuditLogger.AsyncAuditStats stats = asyncLogger.getStats();
        
        assertEquals(2, stats.queued());
        assertTrue(stats.processed() >= 0);
        assertTrue(stats.successRate() >= 0);
    }
    
    @Test
    void testShutdown() {
        asyncLogger.logDataModification(1L, "CREATE", "sales", 1L, null, "data");
        
        asyncLogger.shutdown();
        
        AsyncAuditLogger.AsyncAuditStats stats = asyncLogger.getStats();
        assertTrue(stats.queued() > 0 || stats.processed() > 0);
    }
}

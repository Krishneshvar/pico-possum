package com.possum.application.auth;

import com.possum.domain.repositories.AuditRepository;

public class ServiceSecurity {

    public static void setAuditRepository(AuditRepository repo) {
    }

    public static void requirePermission(String permission) {
        // No-op for single-user application
    }
}

package com.picopossum.application.auth;

import com.picopossum.domain.repositories.AuditRepository;

public class ServiceSecurity {

    public static void setAuditRepository(AuditRepository repo) {
    }

    public static void requirePermission(String permission) {
        // No-op for single-user application
    }
}

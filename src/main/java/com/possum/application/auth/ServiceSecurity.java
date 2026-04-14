package com.possum.application.auth;

import com.possum.domain.exceptions.AuthorizationException;
import com.possum.domain.model.AuditLog;
import com.possum.domain.repositories.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceSecurity {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSecurity.class);
    private static final AuthorizationService authService = new AuthorizationService();
    private static AuditRepository auditRepository;

    public static void setAuditRepository(AuditRepository repo) {
        auditRepository = repo;
    }

    public static void requirePermission(String permission) {
        AuthUser currentUser = AuthContext.getCurrentUser();
        if (currentUser == null) {
            LOGGER.warn("ACCESS_DENIED permission={} reason=no_active_session", permission);
            insertAccessDeniedAudit(null, permission, "no_active_session");
            throw new AuthorizationException("Unauthorized: No active user session.");
        }
        UserContext userContext = new UserContext(
                currentUser.id(),
                currentUser.roles(),
                currentUser.permissions()
        );
        if (!authService.hasPermission(userContext, permission)) {
            LOGGER.warn("ACCESS_DENIED userId={} username={} permission={}",
                    currentUser.id(), currentUser.username(), permission);
            insertAccessDeniedAudit(currentUser.id(), permission, "insufficient_permissions");
            throw new AuthorizationException("Forbidden: Missing required permission (" + permission + ")");
        }
    }

    private static void insertAccessDeniedAudit(Long userId, String permission, String reason) {
        if (auditRepository == null) return;
        try {
            String details = "{\"permission\":\"" + permission + "\",\"reason\":\"" + reason + "\"}";
            auditRepository.insertAuditLog(new AuditLog(
                    null, userId, "ACCESS_DENIED", "auth", null, null, null, details, null,
                    com.possum.shared.util.TimeUtil.nowUTC()));
        } catch (Exception ignored) {
            // Audit failures must never break auth flow
        }
    }
}

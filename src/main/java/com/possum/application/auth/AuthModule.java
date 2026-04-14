package com.possum.application.auth;

import com.possum.application.auth.handlers.AuthHandler;
import com.possum.infrastructure.security.PasswordHasher;
import com.possum.persistence.db.TransactionManager;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.SessionRepository;
import com.possum.domain.repositories.UserRepository;

public class AuthModule {

    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final SessionService sessionService;
    private final AuthMiddleware authMiddleware;
    private final AuthHandler authHandler;

    public AuthModule(UserRepository userRepository, SessionRepository sessionRepository,
                      TransactionManager transactionManager, PasswordHasher passwordHasher) {
        this(userRepository, sessionRepository, transactionManager, passwordHasher, null);
    }

    public AuthModule(UserRepository userRepository, SessionRepository sessionRepository,
                      TransactionManager transactionManager, PasswordHasher passwordHasher,
                      AuditRepository auditRepository) {
        this.sessionService = new SessionService(sessionRepository, userRepository);
        this.authService = new AuthService(userRepository, sessionRepository, sessionService,
                passwordHasher, new LoginAttemptTracker(), auditRepository);
        this.authorizationService = new AuthorizationService();
        this.authMiddleware = new AuthMiddleware(authService, authorizationService);
        this.authHandler = new AuthHandler(authService);
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AuthorizationService getAuthorizationService() {
        return authorizationService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public AuthMiddleware getAuthMiddleware() {
        return authMiddleware;
    }

    public AuthHandler getAuthHandler() {
        return authHandler;
    }
}

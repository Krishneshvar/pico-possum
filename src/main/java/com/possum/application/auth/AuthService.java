package com.possum.application.auth;

import com.possum.domain.exceptions.AuthenticationException;
import com.possum.domain.model.AuditLog;
import com.possum.domain.model.Role;
import com.possum.domain.model.SessionRecord;
import com.possum.domain.model.User;
import com.possum.infrastructure.security.PasswordHasher;
import com.possum.domain.repositories.AuditRepository;
import com.possum.domain.repositories.SessionRepository;
import com.possum.domain.repositories.UserRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.possum.shared.util.TimeUtil;
import java.time.LocalDateTime;

public class AuthService {

    private static final int SESSION_DURATION_SECONDS = 30 * 60;
    private static final int CLEANUP_INTERVAL_SECONDS = 10 * 60;
    private static final String DUMMY_HASH = "$2a$10$InCX8UtTmhbQP3NuHPaRAeCdfZeaIngIzsAjWjbAYxjprs6WHcoAG";
    private static final String LEGACY_DEFAULT_ADMIN_USERNAME = "admin";
    private static final String LEGACY_DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final PasswordHasher passwordHasher;
    private final LoginAttemptTracker attemptTracker;
    private final AuditRepository auditRepository;
    private volatile long lastCleanupTime = 0;

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            SessionService sessionService,
            PasswordHasher passwordHasher
    ) {
        this(userRepository, sessionRepository, sessionService, passwordHasher, new LoginAttemptTracker(), null);
    }

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            SessionService sessionService,
            PasswordHasher passwordHasher,
            LoginAttemptTracker attemptTracker
    ) {
        this(userRepository, sessionRepository, sessionService, passwordHasher, attemptTracker, null);
    }

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            SessionService sessionService,
            PasswordHasher passwordHasher,
            LoginAttemptTracker attemptTracker,
            AuditRepository auditRepository
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.passwordHasher = passwordHasher;
        this.attemptTracker = attemptTracker;
        this.auditRepository = auditRepository;
    }

    public LoginResponse login(String username, String password) {
        if (attemptTracker.isLocked(username)) {
            long seconds = attemptTracker.secondsUntilUnlock(username);
            throw new AuthenticationException(
                    "Account temporarily locked. Try again in " + seconds + " seconds.");
        }

        Optional<User> userOpt = userRepository.findUserByUsername(username);
        
        if (userOpt.isEmpty() || userOpt.get().deletedAt() != null) {
            passwordHasher.verifyPassword(password, DUMMY_HASH); // Prevent timing attacks
            attemptTracker.recordFailure(username);
            insertSecurityAudit(null, "LOGIN_FAILED", "auth",
                    java.util.Map.of("username", username, "reason", "user_not_found"));
            throw new AuthenticationException("Invalid username or password");
        }

        User user = userOpt.get();
        if (!Boolean.TRUE.equals(user.active())) {
            passwordHasher.verifyPassword(password, DUMMY_HASH); // Prevent timing attacks
            attemptTracker.recordFailure(username);
            insertSecurityAudit(user.id(), "LOGIN_FAILED", "auth",
                    java.util.Map.of("username", username, "reason", "account_inactive"));
            throw new AuthenticationException("Account is inactive. Please contact administrator.");
        }
        boolean isValid = passwordHasher.verifyPassword(password, user.passwordHash());

        if (!isValid) {
            attemptTracker.recordFailure(username);
            insertSecurityAudit(user.id(), "LOGIN_FAILED", "auth",
                    java.util.Map.of("username", username, "reason", "wrong_password"));
            throw new AuthenticationException("Invalid username or password");
        }

        attemptTracker.recordSuccess(username);

        // Check if this is the legacy default account that needs rotation
        boolean mustRotate = username.equals(LEGACY_DEFAULT_ADMIN_USERNAME) && 
                            password.equals(LEGACY_DEFAULT_ADMIN_PASSWORD);

        AuthUser userData = sessionService.buildAuthUser(user.id());
        String token = sessionService.createSession(userData, "127.0.0.1", "POSSUM-Desktop");

        MDC.put("userId", String.valueOf(userData.id()));
        MDC.put("username", userData.username());

        return new LoginResponse(userData, token, mustRotate);
    }

    public void rotateDefaultAdminPassword(String username, String currentPassword, String newPassword) {
        if (!isLegacyDefaultAdminPasswordInUse()) {
            throw new AuthenticationException("Default password is no longer in use");
        }

        AuthUser user = login(username, currentPassword).user();
        if (user == null) {
            throw new AuthenticationException("Invalid current credentials");
        }

        Optional<User> uOpt = userRepository.findUserById(user.id());
        if (uOpt.isPresent()) {
            User existing = uOpt.get();
            List<Long> currentRoleIds = userRepository.getUserRoles(existing.id()).stream().map(Role::id).toList();
            User updated = new User(existing.id(), existing.name(), existing.username(), 
                passwordHasher.hashPassword(newPassword), existing.active(), existing.createdAt(), 
                TimeUtil.nowUTC(), existing.deletedAt());
            userRepository.updateUserWithRolesById(existing.id(), updated, currentRoleIds);
        }
    }

    public AuthUser validateSession(String token) {

        long now = System.currentTimeMillis() / 1000;

        if (now - lastCleanupTime > CLEANUP_INTERVAL_SECONDS) {
            lastCleanupTime = now;
            sessionService.deleteExpiredSessions();
        }

        Optional<SessionRecord> sessionOpt = sessionService.findByToken(token);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        SessionRecord session = sessionOpt.get();

        Optional<User> currentUserOpt = userRepository.findUserById(session.userId());
        if (currentUserOpt.isEmpty() || !currentUserOpt.get().active() || currentUserOpt.get().deletedAt() != null) {
            sessionService.deleteSession(token);
            return null;
        }

        if (session.expiresAt() < now) {
            sessionService.deleteSession(token);
            return null;
        }

        AuthUser authUser = sessionService.buildAuthUser(session.userId());

        long newExpiresAt = now + SESSION_DURATION_SECONDS;
        sessionService.updateExpiration(token, newExpiresAt);

        return authUser;
    }

    public void logout(String token) {
        sessionService.deleteSession(token);
        MDC.remove("userId");
        MDC.remove("username");
    }

    public AuthUser getCurrentUser(long userId) {
        return sessionService.buildAuthUser(userId);
    }

    public void revokeUserSessions(long userId) {
        sessionService.deleteUserSessions(userId);
    }

    public void clearAllSessions() {
        sessionRepository.deleteAll();
    }

    public AuthBootstrapStatus getAuthBootstrapStatus() {
        boolean requiresInitialSetup = !hasAnyActiveUsers();
        // We no longer force rotation on startup, we do it during login
        return new AuthBootstrapStatus(requiresInitialSetup, false);
    }

    public LoginResponse setupInitialAdmin(String name, String username, String password) {
        AuthBootstrapStatus status = getAuthBootstrapStatus();
        if (!status.requiresInitialSetup()) {
            throw new AuthenticationException("Initial setup has already been completed");
        }

        String trimmedName = name.trim();
        String trimmedUsername = username.trim();
        if (trimmedName.isEmpty()) throw new AuthenticationException("Name is required");
        if (trimmedUsername.length() < 3) throw new AuthenticationException("Username must be at least 3 characters");
        if (password.length() < 8) throw new AuthenticationException("Password must be at least 8 characters");

        Optional<User> existing = userRepository.findUserByUsername(trimmedUsername);
        if (existing.isPresent()) {
            throw new AuthenticationException("Username already exists");
        }

        Optional<Role> adminRole = userRepository.getAllRoles().stream()
                .filter(role -> role.name().equalsIgnoreCase("admin"))
                .findFirst();

        if (adminRole.isEmpty()) {
            throw new AuthenticationException("Admin role is missing from data");
        }

        String passwordHash = passwordHasher.hashPassword(password);
        LocalDateTime now = TimeUtil.nowUTC();
        User user = new User(0L, trimmedName, trimmedUsername, passwordHash, true, now, now, null);
        User createdUser = userRepository.insertUserWithRoles(user, Collections.singletonList(adminRole.get().id()));

        AuthUser authUser = sessionService.buildAuthUser(createdUser.id());
        String token = sessionService.createSession(authUser, "127.0.0.1", "POSSUM-Desktop");
        return new LoginResponse(authUser, token, false);
    }

    private boolean hasAnyActiveUsers() {
        PagedResult<User> usersPage = userRepository.findUsers(new UserFilter(null, 1, 1, null, null));
        return usersPage.totalCount() > 0;
    }

    private boolean isLegacyDefaultAdminPasswordInUse() {
        return userRepository.findUserByUsername(LEGACY_DEFAULT_ADMIN_USERNAME)
                .filter(u -> u.active() && u.deletedAt() == null)
                .map(u -> passwordHasher.verifyPassword(LEGACY_DEFAULT_ADMIN_PASSWORD, u.passwordHash()))
                .orElse(false);
    }

    private void insertSecurityAudit(Long userId, String action, String tableName, Object eventDetails) {
        if (auditRepository == null) return;
        try {
            String details = eventDetails instanceof String s ? s
                    : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(eventDetails);
            auditRepository.insertAuditLog(new AuditLog(
                    null, userId, action, tableName, null, null, null, details, null,
                    com.possum.shared.util.TimeUtil.nowUTC()));
        } catch (Exception ignored) {
            // Audit failures must never break auth flow
        }
    }
}

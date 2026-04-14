package com.possum.application.auth;

import com.possum.domain.exceptions.DomainException;
import com.possum.domain.model.Role;
import com.possum.domain.model.SessionRecord;
import com.possum.domain.model.User;
import com.possum.infrastructure.security.SecureTokenGenerator;
import com.possum.domain.repositories.SessionRepository;
import com.possum.domain.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SessionService {

    private static final int SESSION_DURATION_SECONDS = 30 * 60;
    private static final int MAX_SESSIONS_PER_USER = 5;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public String createSession(AuthUser user, String ipAddress, String userAgent) {
        String token = SecureTokenGenerator.generateToken();
        String fingerprint = SecureTokenGenerator.generateSessionFingerprint(ipAddress, userAgent);
        long expiresAt = System.currentTimeMillis() / 1000 + SESSION_DURATION_SECONDS;

        SessionRecord session = new SessionRecord(
                UUID.randomUUID().toString(),
                user.id(),
                token,
                expiresAt,
                fingerprint
        );

        sessionRepository.create(session);
        return token;
    }

    public Optional<SessionRecord> findByToken(String token) {
        return sessionRepository.findByToken(token);
    }
    
    public boolean validateSessionFingerprint(SessionRecord session, String ipAddress, String userAgent) {
        if (session.data() == null) {
            return true;
        }
        String currentFingerprint = SecureTokenGenerator.generateSessionFingerprint(ipAddress, userAgent);
        return session.data().equals(currentFingerprint);
    }

    public void updateExpiration(String token, long newExpiresAt) {
        sessionRepository.updateExpiration(token, newExpiresAt);
    }

    public void deleteSession(String token) {
        sessionRepository.deleteByToken(token);
    }

    public void deleteUserSessions(long userId) {
        sessionRepository.deleteByUserId(userId);
    }

    public void deleteExpiredSessions() {
        long now = System.currentTimeMillis() / 1000;
        sessionRepository.deleteExpired(now);
    }

    public AuthUser buildAuthUser(long userId) {
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new DomainException("User not found or inactive"));

        if (!user.active() || user.deletedAt() != null) {
            throw new DomainException("User not found or inactive");
        }

        List<String> permissions = userRepository.getUserPermissions(userId);
        List<String> roles = userRepository.getUserRoles(userId).stream()
                .map(Role::name)
                .toList();

        return new AuthUser(user.id(), user.name(), user.username(), roles, permissions);
    }
}

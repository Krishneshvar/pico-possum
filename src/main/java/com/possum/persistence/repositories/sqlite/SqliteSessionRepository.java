package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.SessionRecord;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.domain.repositories.SessionRepository;

import java.util.Optional;
import java.util.UUID;

public final class SqliteSessionRepository extends BaseSqliteRepository implements SessionRepository {

    public SqliteSessionRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public void create(SessionRecord session) {
        boolean userExists = queryOne(
                "SELECT id FROM users WHERE id = ? AND is_active = 1 AND deleted_at IS NULL",
                rs -> rs.getLong("id"),
                session.userId()
        ).isPresent();
        if (!userExists) {
            throw new IllegalStateException("Cannot create session for inactive user: " + session.userId());
        }
        String id = session.id() == null ? UUID.randomUUID().toString() : session.id();
        executeInsert(
                "INSERT INTO sessions (id, user_id, token, expires_at, data) VALUES (?, ?, ?, ?, ?)",
                id,
                session.userId(),
                session.token(),
                session.expiresAt(),
                session.data()
        );
    }

    @Override
    public Optional<SessionRecord> findByToken(String token) {
        return queryOne(
                "SELECT id, user_id, token, expires_at, data FROM sessions WHERE token = ?",
                rs -> new SessionRecord(
                        rs.getString("id"),
                        rs.getLong("user_id"),
                        rs.getString("token"),
                        rs.getLong("expires_at"),
                        rs.getString("data")
                ),
                token
        );
    }

    @Override
    public void updateExpiration(String token, long newExpiresAt) {
        executeUpdate("UPDATE sessions SET expires_at = ? WHERE token = ?", newExpiresAt, token);
    }

    @Override
    public void deleteByToken(String token) {
        executeUpdate("DELETE FROM sessions WHERE token = ?", token);
    }

    @Override
    public void deleteExpired(long now) {
        executeUpdate("DELETE FROM sessions WHERE expires_at < ?", now);
    }

    @Override
    public void deleteAll() {
        executeUpdate("DELETE FROM sessions");
    }

    @Override
    public void deleteByUserId(long userId) {
        executeUpdate("DELETE FROM sessions WHERE user_id = ?", userId);
    }
}

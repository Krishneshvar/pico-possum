package com.possum.domain.repositories;

import com.possum.domain.model.SessionRecord;

import java.util.Optional;

public interface SessionRepository {
    void create(SessionRecord session);

    Optional<SessionRecord> findByToken(String token);

    void updateExpiration(String token, long newExpiresAt);

    void deleteByToken(String token);

    void deleteExpired(long now);

    void deleteAll();

    void deleteByUserId(long userId);
}

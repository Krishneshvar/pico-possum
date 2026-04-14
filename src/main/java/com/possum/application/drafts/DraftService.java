package com.possum.application.drafts;

import com.possum.persistence.repositories.sqlite.BaseSqliteRepository;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.persistence.db.ConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Service for persisting and recovering unfinished form data (Drafts).
 * Prevents data loss during unexpected shutdowns or session timeouts.
 */
public class DraftService extends BaseSqliteRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DraftService.class);
    private final JsonService jsonService;

    public DraftService(ConnectionProvider connectionProvider, JsonService jsonService) {
        super(connectionProvider);
        this.jsonService = jsonService;
    }

    /**
     * Saves or updates a draft.
     */
    public void saveDraft(String id, String type, Object payload, Long userId) {
        try {
            String json = jsonService.toJson(payload);
            executeUpdate(
                "INSERT INTO drafts (id, type, payload, user_id, updated_at) " +
                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT(id) DO UPDATE SET payload=excluded.payload, updated_at=excluded.updated_at",
                id, type, json, userId
            );
            LOGGER.info("Saved {} draft for user {}: {}", type, userId, id);
        } catch (Exception e) {
            LOGGER.error("Failed to save draft {}: {}", id, e.getMessage());
        }
    }

    /**
     * Recovers a draft payload and maps it to the specified class.
     */
    public <T> Optional<T> recoverDraft(String id, Class<T> clazz) {
        return queryOne(
            "SELECT payload FROM drafts WHERE id = ?",
            rs -> {
                try {
                    LOGGER.info("Recovered {} draft: {}", clazz.getSimpleName(), id);
                    return jsonService.fromJson(rs.getString("payload"), clazz);
                } catch (Exception e) {
                    LOGGER.error("Failed to deserialize draft {}: {}", id, e.getMessage());
                    return null;
                }
            },
            id
        );
    }

    /**
     * Deletes a draft (e.g., after successful submission of the form).
     */
    public void deleteDraft(String id) {
        executeUpdate("DELETE FROM drafts WHERE id = ?", id);
        LOGGER.debug("Deleted draft: {}", id);
    }

    /**
     * Checks if a draft exists for a given ID.
     */
    public boolean exists(String id) {
        return queryOne("SELECT 1 FROM drafts WHERE id = ?", rs -> true, id).isPresent();
    }
}

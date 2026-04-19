package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.User;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.mappers.UserMapper;
import com.picopossum.domain.repositories.UserRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.UserFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteUserRepository extends BaseSqliteRepository implements UserRepository {

    private final UserMapper userMapper = new UserMapper();

    public SqliteUserRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public PagedResult<User> findUsers(UserFilter filter) {
        int page = Math.max(1, filter.page() == null ? 1 : filter.page());
        int limit = Math.max(1, filter.limit() == null ? 10 : filter.limit());
        int offset = (page - 1) * limit;

        WhereBuilder whereBuilder = new WhereBuilder()
                .addNotDeleted()
                .addSearch(filter.searchTerm(), "name", "username");

        if (filter.activeStatuses() != null && !filter.activeStatuses().isEmpty()) {
            List<Integer> activeInts = filter.activeStatuses().stream().map(b -> b ? 1 : 0).toList();
            whereBuilder.addIn("is_active", activeInts);
        }

        String where = whereBuilder.build();
        List<Object> params = new ArrayList<>(whereBuilder.getParams());

        int totalCount = count("users", where, params.toArray());

        params.add(limit);
        params.add(offset);
        List<User> users = queryList(
                """
                SELECT id, name, username, password_hash, is_active, created_at, updated_at, deleted_at
                FROM users
                %s
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(where),
                userMapper,
                params.toArray()
        );

        int totalPages = (int) Math.ceil((double) totalCount / limit);
        return new PagedResult<>(users, totalCount, totalPages, page, limit);
    }

    @Override
    public Optional<User> findUserById(long id) {
        return queryOne(
                "SELECT * FROM users WHERE id = ? AND deleted_at IS NULL",
                userMapper,
                id
        );
    }

    @Override
    public Optional<User> findUserByUsername(String username) {
        return queryOne(
                "SELECT * FROM users WHERE username = ? AND deleted_at IS NULL",
                userMapper,
                username
        );
    }

    @Override
    public User insertUser(User user) {
        long userId = executeInsert(
                "INSERT INTO users (name, username, password_hash, is_active) VALUES (?, ?, ?, ?)",
                user.name(),
                user.username(),
                user.passwordHash(),
                boolToInt(user.active(), true)
        );
        return findUserById(userId).orElseThrow();
    }

    @Override
    public User updateUserById(long id, User userData) {
        UpdateBuilder builder = new UpdateBuilder("users")
                .set("name", userData.name())
                .set("username", userData.username())
                .set("password_hash", userData.passwordHash())
                .set("is_active", userData.active() != null ? boolToInt(userData.active(), true) : null)
                .where("id = ?", id);

        executeUpdate(builder.getSql(), builder.getParams());
        return findUserById(id).orElseThrow();
    }

    @Override
    public boolean softDeleteUser(long id) {
        return executeUpdate("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id) > 0;
    }

    @Override
    public void revokeUserSessions(long userId) {
        executeUpdate("DELETE FROM sessions WHERE user_id = ?", userId);
    }
}

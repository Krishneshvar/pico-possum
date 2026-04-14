package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.Permission;
import com.possum.domain.model.Role;
import com.possum.domain.model.User;
import com.possum.domain.model.UserPermissionOverride;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.PermissionMapper;
import com.possum.persistence.mappers.RoleMapper;
import com.possum.persistence.mappers.UserMapper;
import com.possum.domain.repositories.UserRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public final class SqliteUserRepository extends BaseSqliteRepository implements UserRepository {

    private final UserMapper userMapper = new UserMapper();
    private final RoleMapper roleMapper = new RoleMapper();
    private final PermissionMapper permissionMapper = new PermissionMapper();

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
    public User insertUserWithRoles(User user, List<Long> roleIds) {
        long userId = executeInsert(
                "INSERT INTO users (name, username, password_hash, is_active) VALUES (?, ?, ?, ?)",
                user.name(),
                user.username(),
                user.passwordHash(),
                boolToInt(user.active(), true)
        );
        for (Long roleId : roleIds) {
            executeUpdate("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        return findUserById(userId).orElseThrow();
    }

    @Override
    public User updateUserWithRolesById(long id, User userData, List<Long> roleIds) {
        UpdateBuilder builder = new UpdateBuilder("users")
                .set("name", userData.name())
                .set("username", userData.username())
                .set("password_hash", userData.passwordHash())
                .set("is_active", userData.active() != null ? boolToInt(userData.active(), true) : null)
                .where("id = ?", id);

        executeUpdate(builder.getSql(), builder.getParams());

        if (roleIds != null) {
            executeUpdate("DELETE FROM user_roles WHERE user_id = ?", id);
            for (Long roleId : roleIds) {
                executeUpdate("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", id, roleId);
            }
            executeUpdate("DELETE FROM sessions WHERE user_id = ?", id);
        }
        return findUserById(id).orElseThrow();
    }

    @Override
    public boolean softDeleteUser(long id) {
        return executeUpdate("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id) > 0;
    }

    @Override
    public List<Role> getAllRoles() {
        return queryList("SELECT * FROM roles ORDER BY name ASC", roleMapper);
    }

    @Override
    public List<Permission> getAllPermissions() {
        return queryList("SELECT * FROM permissions ORDER BY key ASC", permissionMapper);
    }

    @Override
    public List<Role> getUserRoles(long userId) {
        return queryList(
                """
                SELECT r.id, r.name, r.description
                FROM roles r
                JOIN user_roles ur ON r.id = ur.role_id
                WHERE ur.user_id = ?
                """,
                roleMapper,
                userId
        );
    }

    @Override
    public List<String> getUserPermissions(long userId) {
        List<String> permissions = queryList(
                """
                SELECT DISTINCT p.key AS key
                FROM permissions p
                JOIN role_permissions rp ON p.id = rp.permission_id
                JOIN user_roles ur ON rp.role_id = ur.role_id
                WHERE ur.user_id = ?
                """,
                rs -> rs.getString("key"),
                userId
        );

        List<UserPermissionOverride> overrides = getUserPermissionOverrides(userId);
        java.util.Set<String> permissionSet = new java.util.HashSet<>(permissions);
        for (UserPermissionOverride override : overrides) {
            if (Boolean.TRUE.equals(override.granted())) {
                permissionSet.add(override.permissionKey());
            } else {
                permissionSet.remove(override.permissionKey());
            }
        }
        return new ArrayList<>(permissionSet);
    }

    @Override
    public void assignUserRoles(long userId, List<Long> roleIds) {
        executeUpdate("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (Long roleId : roleIds) {
            executeUpdate("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    @Override
    public List<UserPermissionOverride> getUserPermissionOverrides(long userId) {
        return queryList(
                """
                SELECT up.user_id, up.permission_id, p.key, up.granted
                FROM user_permissions up
                JOIN permissions p ON up.permission_id = p.id
                WHERE up.user_id = ?
                """,
                rs -> new UserPermissionOverride(
                        rs.getLong("user_id"),
                        rs.getLong("permission_id"),
                        rs.getString("key"),
                        rs.getInt("granted") == 1
                ),
                userId
        );
    }

    @Override
    public void setUserPermission(long userId, long permissionId, boolean granted) {
        executeUpdate(
                """
                INSERT OR REPLACE INTO user_permissions (user_id, permission_id, granted, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                userId,
                permissionId,
                granted ? 1 : 0
        );
        executeUpdate("DELETE FROM sessions WHERE user_id = ?", userId);
    }

    @Override
    public List<Long> getRolePermissions(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return new ArrayList<>();
        String placeholders = "?,".repeat(roleIds.size()).replaceAll(",$", "");
        return queryList(
                "SELECT DISTINCT permission_id FROM role_permissions WHERE role_id IN (" + placeholders + ")",
                rs -> rs.getLong("permission_id"),
                roleIds.toArray()
        );
    }

    @Override
    public void revokeUserSessions(long userId) {
        executeUpdate("DELETE FROM sessions WHERE user_id = ?", userId);
    }
}

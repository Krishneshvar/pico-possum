package com.possum.persistence.repositories;

import com.possum.domain.model.Permission;
import com.possum.domain.model.Role;
import com.possum.domain.model.User;
import com.possum.domain.model.UserPermissionOverride;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.repositories.sqlite.SqliteUserRepository;
import com.possum.shared.dto.PagedResult;
import com.possum.shared.dto.UserFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SqliteUserRepositoryTest {

    private ConnectionProvider connectionProvider;
    private Connection connection;
    private SqliteUserRepository repository;

    @BeforeEach
    void setUp() {
        connectionProvider = mock(ConnectionProvider.class);
        connection = mock(Connection.class);
        when(connectionProvider.getConnection()).thenReturn(connection);
        repository = new SqliteUserRepository(connectionProvider);
    }

    @Test
    void shouldFindUserById() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("John");
        when(rs.getString("username")).thenReturn("john");
        when(rs.getString("password_hash")).thenReturn("hash");
        when(rs.getInt("is_active")).thenReturn(1);

        Optional<User> result = repository.findUserById(1L);

        assertTrue(result.isPresent());
        assertEquals("John", result.get().name());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        Optional<User> result = repository.findUserById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindUserByUsername() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("username")).thenReturn("john");

        Optional<User> result = repository.findUserByUsername("john");

        assertTrue(result.isPresent());
        verify(stmt).setObject(1, "john");
    }

    @Test
    void shouldFindUsersWithFilter() throws SQLException {
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement queryStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet queryRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(countStmt)
                .thenReturn(queryStmt);
        when(countStmt.executeQuery()).thenReturn(countRs);
        when(queryStmt.executeQuery()).thenReturn(queryRs);
        when(countRs.next()).thenReturn(true);
        when(countRs.getInt("count")).thenReturn(1);
        when(queryRs.next()).thenReturn(true).thenReturn(false);
        when(queryRs.getLong("id")).thenReturn(1L);
        when(queryRs.getString("username")).thenReturn("john");
        when(queryRs.getInt("is_active")).thenReturn(1);

        UserFilter filter = new UserFilter("john", 1, 10, List.of(true), null);
        PagedResult<User> result = repository.findUsers(filter);

        assertEquals(1, result.totalCount());
        assertEquals(1, result.items().size());
    }

    @Test
    void shouldHandleNullPageAndLimit() throws SQLException {
        PreparedStatement countStmt = mock(PreparedStatement.class);
        PreparedStatement queryStmt = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        ResultSet queryRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(countStmt)
                .thenReturn(queryStmt);
        when(countStmt.executeQuery()).thenReturn(countRs);
        when(queryStmt.executeQuery()).thenReturn(queryRs);
        when(countRs.next()).thenReturn(true);
        when(countRs.getInt("count")).thenReturn(0);
        when(queryRs.next()).thenReturn(false);

        UserFilter filter = new UserFilter(null, null, null, null, null);
        PagedResult<User> result = repository.findUsers(filter);

        assertEquals(0, result.totalCount());
    }

    @Test
    void shouldGetAllRoles() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("admin");
        when(rs.getString("description")).thenReturn("Administrator");

        List<Role> result = repository.getAllRoles();

        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).name());
    }

    @Test
    void shouldGetAllPermissions() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("key")).thenReturn("sales.create");
        when(rs.getString("description")).thenReturn("Create sales");

        List<Permission> result = repository.getAllPermissions();

        assertEquals(1, result.size());
        assertEquals("sales.create", result.get(0).key());
    }

    @Test
    void shouldGetUserRoles() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("admin");

        List<Role> result = repository.getUserRoles(1L);

        assertEquals(1, result.size());
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldGetUserPermissions() throws SQLException {
        PreparedStatement permStmt = mock(PreparedStatement.class);
        PreparedStatement overrideStmt = mock(PreparedStatement.class);
        ResultSet permRs = mock(ResultSet.class);
        ResultSet overrideRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(permStmt)
                .thenReturn(overrideStmt);
        when(permStmt.executeQuery()).thenReturn(permRs);
        when(overrideStmt.executeQuery()).thenReturn(overrideRs);
        when(permRs.next()).thenReturn(true).thenReturn(false);
        when(permRs.getString("key")).thenReturn("sales.create");
        when(overrideRs.next()).thenReturn(false);

        List<String> result = repository.getUserPermissions(1L);

        assertEquals(1, result.size());
        assertEquals("sales.create", result.get(0));
    }

    @Test
    void shouldApplyPermissionOverrides() throws SQLException {
        PreparedStatement permStmt = mock(PreparedStatement.class);
        PreparedStatement overrideStmt = mock(PreparedStatement.class);
        ResultSet permRs = mock(ResultSet.class);
        ResultSet overrideRs = mock(ResultSet.class);

        when(connection.prepareStatement(anyString()))
                .thenReturn(permStmt)
                .thenReturn(overrideStmt);
        when(permStmt.executeQuery()).thenReturn(permRs);
        when(overrideStmt.executeQuery()).thenReturn(overrideRs);
        when(permRs.next()).thenReturn(true).thenReturn(false);
        when(permRs.getString("key")).thenReturn("sales.create");
        when(overrideRs.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(overrideRs.getLong("user_id")).thenReturn(1L);
        when(overrideRs.getLong("permission_id")).thenReturn(1L, 2L);
        when(overrideRs.getString("key")).thenReturn("sales.create", "returns.manage");
        when(overrideRs.getInt("granted")).thenReturn(0, 1);

        List<String> result = repository.getUserPermissions(1L);

        assertFalse(result.contains("sales.create"));
        assertTrue(result.contains("returns.manage"));
    }

    @Test
    void shouldGetUserPermissionOverrides() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(false);
        when(rs.getLong("user_id")).thenReturn(1L);
        when(rs.getLong("permission_id")).thenReturn(2L);
        when(rs.getString("key")).thenReturn("sales.create");
        when(rs.getInt("granted")).thenReturn(1);

        List<UserPermissionOverride> result = repository.getUserPermissionOverrides(1L);

        assertEquals(1, result.size());
        assertEquals("sales.create", result.get(0).permissionKey());
        assertTrue(result.get(0).granted());
    }

    @Test
    void shouldAssignUserRoles() throws SQLException {
        PreparedStatement deleteStmt = mock(PreparedStatement.class);
        PreparedStatement insertStmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString()))
                .thenReturn(deleteStmt)
                .thenReturn(insertStmt)
                .thenReturn(insertStmt);
        when(deleteStmt.executeUpdate()).thenReturn(1);
        when(insertStmt.executeUpdate()).thenReturn(1);

        repository.assignUserRoles(1L, List.of(1L, 2L));

        verify(deleteStmt).setObject(1, 1L);
        verify(deleteStmt).executeUpdate();
        verify(insertStmt, times(2)).executeUpdate();
    }

    @Test
    void shouldSetUserPermission() throws SQLException {
        PreparedStatement updateStmt = mock(PreparedStatement.class);
        PreparedStatement deleteStmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString()))
                .thenReturn(updateStmt)
                .thenReturn(deleteStmt);
        when(updateStmt.executeUpdate()).thenReturn(1);
        when(deleteStmt.executeUpdate()).thenReturn(0);

        repository.setUserPermission(1L, 2L, true);

        verify(updateStmt).setObject(1, 1L);
        verify(updateStmt).setObject(2, 2L);
        verify(updateStmt).setObject(3, 1);
        verify(deleteStmt).setObject(1, 1L);
    }

    @Test
    void shouldGetRolePermissions() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(rs.getLong("permission_id")).thenReturn(1L, 2L);

        List<Long> result = repository.getRolePermissions(List.of(1L, 2L));

        assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyForNullRoleIds() {
        List<Long> result = repository.getRolePermissions(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyRoleIds() {
        List<Long> result = repository.getRolePermissions(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRevokeUserSessions() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(2);

        repository.revokeUserSessions(1L);

        verify(stmt).setObject(1, 1L);
        verify(stmt).executeUpdate();
    }

    @Test
    void shouldSoftDeleteUser() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(1);

        boolean result = repository.softDeleteUser(1L);

        assertTrue(result);
        verify(stmt).setObject(1, 1L);
    }

    @Test
    void shouldReturnFalseWhenSoftDeleteAffectsNoRows() throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        boolean result = repository.softDeleteUser(999L);

        assertFalse(result);
    }
}

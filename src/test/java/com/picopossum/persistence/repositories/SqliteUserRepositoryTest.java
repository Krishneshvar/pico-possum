package com.picopossum.persistence.repositories;

import com.picopossum.domain.model.User;
import com.picopossum.persistence.db.ConnectionProvider;
import com.picopossum.persistence.repositories.sqlite.SqliteUserRepository;
import com.picopossum.shared.dto.PagedResult;
import com.picopossum.shared.dto.UserFilter;
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

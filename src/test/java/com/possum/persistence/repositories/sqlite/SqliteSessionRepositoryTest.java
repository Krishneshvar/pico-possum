package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.SessionRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSessionRepositoryTest {

    private Connection connection;
    private SqliteSessionRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteSessionRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void createSchema() throws SQLException {
        connection.createStatement().execute("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                is_active INTEGER DEFAULT 1,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE sessions (
                id TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                token TEXT UNIQUE NOT NULL,
                expires_at INTEGER NOT NULL,
                data TEXT,
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
            )
        """);
    }

    @Test
    void create_validSession_insertsSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        
        SessionRecord session = new SessionRecord("sid-1", 1L, "token-xyz", System.currentTimeMillis() + 10000, "{}");
        assertDoesNotThrow(() -> repository.create(session));
        
        Optional<SessionRecord> found = repository.findByToken("token-xyz");
        assertTrue(found.isPresent());
        assertEquals(1L, found.get().userId());
    }

    @Test
    void create_inactiveUser_throwsException() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash, is_active) VALUES (1, 'Admin', 'admin', 'hash', 0)");
        
        SessionRecord session = new SessionRecord("sid-1", 1L, "token-xyz", System.currentTimeMillis() + 10000, "{}");
        assertThrows(IllegalStateException.class, () -> repository.create(session));
    }

    @Test
    void findByToken_found_returnsSession() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        SessionRecord session = new SessionRecord("sid-2", 1L, "token-abc", System.currentTimeMillis() + 10000, "{}");
        repository.create(session);

        Optional<SessionRecord> result = repository.findByToken("token-abc");

        assertTrue(result.isPresent());
        assertEquals("sid-2", result.get().id());
        assertEquals("token-abc", result.get().token());
    }

    @Test
    void findByToken_notFound_returnsEmpty() {
        Optional<SessionRecord> result = repository.findByToken("invalid-token");
        assertFalse(result.isPresent());
    }

    @Test
    void updateExpiration_updatesSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        SessionRecord session = new SessionRecord("sid-1", 1L, "token-xyz", 100L, "{}");
        repository.create(session);
        
        repository.updateExpiration("token-xyz", 200L);
        
        Optional<SessionRecord> found = repository.findByToken("token-xyz");
        assertTrue(found.isPresent());
        assertEquals(200L, found.get().expiresAt());
    }

    @Test
    void deleteByToken_deletesSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        SessionRecord session = new SessionRecord("sid-1", 1L, "token-xyz", 100L, "{}");
        repository.create(session);
        
        repository.deleteByToken("token-xyz");
        
        assertFalse(repository.findByToken("token-xyz").isPresent());
    }

    @Test
    void deleteExpired_deletesSuccessfully() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        repository.create(new SessionRecord("s1", 1L, "token-1", 100L, "{}"));
        repository.create(new SessionRecord("s2", 1L, "token-2", 300L, "{}"));
        
        repository.deleteExpired(200L);
        
        assertFalse(repository.findByToken("token-1").isPresent());
        assertTrue(repository.findByToken("token-2").isPresent());
    }

    @Test
    void deleteAll_deletesAllSessions() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
        repository.create(new SessionRecord("s1", 1L, "t1", 100L, "{}"));
        repository.create(new SessionRecord("s2", 1L, "t2", 100L, "{}"));

        repository.deleteAll();

        assertFalse(repository.findByToken("t1").isPresent());
        assertFalse(repository.findByToken("t2").isPresent());
    }

    @Test
    void deleteByUserId_deletesAllUserSessions() throws SQLException {
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'User1', 'u1', 'h1')");
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (2, 'User2', 'u2', 'h2')");

        repository.create(new SessionRecord("s1", 1L, "t1", 100L, "{}"));
        repository.create(new SessionRecord("s2", 1L, "t2", 100L, "{}"));
        repository.create(new SessionRecord("s3", 2L, "t3", 100L, "{}"));

        repository.deleteByUserId(1L);

        assertFalse(repository.findByToken("t1").isPresent());
        assertFalse(repository.findByToken("t2").isPresent());
        assertTrue(repository.findByToken("t3").isPresent());
    }
}

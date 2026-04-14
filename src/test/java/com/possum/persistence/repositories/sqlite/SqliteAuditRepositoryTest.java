package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.AuditLog;
import com.possum.shared.dto.AuditLogFilter;
import com.possum.shared.dto.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteAuditRepositoryTest {

    private Connection connection;
    private SqliteAuditRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        createSchema();
        repository = new SqliteAuditRepository(() -> connection);
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
                active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                deleted_at TEXT
            )
        """);
        connection.createStatement().execute("""
            CREATE TABLE audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                action TEXT NOT NULL,
                table_name TEXT NOT NULL,
                row_id INTEGER,
                old_data TEXT,
                new_data TEXT,
                event_details TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
            )
        """);
        connection.createStatement().execute("INSERT INTO users (id, name, username, password_hash) VALUES (1, 'Admin', 'admin', 'hash')");
    }

    @Test
    void insertAuditLog_validLog_insertsSuccessfully() {
        AuditLog log = new AuditLog(null, 1L, "UPDATE", "products", 100L, "{}", "{}", "Product updated", null, LocalDateTime.now());
        long id = repository.insertAuditLog(log);
        assertTrue(id > 0);
    }

    @Test
    void findAuditLogById_found_returnsLog() {
        AuditLog log = new AuditLog(null, 1L, "CREATE", "users", 2L, null, "{}", "User created", null, LocalDateTime.now());
        long id = repository.insertAuditLog(log);

        AuditLog result = repository.findAuditLogById(id);
        assertNotNull(result);
        assertEquals(1L, result.userId());
        assertEquals("CREATE", result.action());
        assertEquals("users", result.tableName());
        assertEquals("Admin", result.userName());
    }

    @Test
    void findAuditLogById_notFound_returnsNull() {
        AuditLog result = repository.findAuditLogById(999L);
        assertNull(result);
    }

    @Test
    void findAuditLogs_withFilters_returnsCorrectResults() {
        repository.insertAuditLog(new AuditLog(null, 1L, "CREATE", "products", 10L, null, "{}", "A", null, LocalDateTime.now()));
        repository.insertAuditLog(new AuditLog(null, 1L, "UPDATE", "products", 10L, "{}", "{}", "B", null, LocalDateTime.now()));
        repository.insertAuditLog(new AuditLog(null, 1L, "DELETE", "categories", 5L, "{}", null, "C", null, LocalDateTime.now()));

        AuditLogFilter filter1 = new AuditLogFilter("products", null, null, null, null, null, null, "id", "ASC", 1, 10);
        PagedResult<AuditLog> result1 = repository.findAuditLogs(filter1);
        assertEquals(2, result1.totalCount());

        AuditLogFilter filter2 = new AuditLogFilter(null, null, null, List.of("DELETE"), null, null, null, "id", "ASC", 1, 10);
        PagedResult<AuditLog> result2 = repository.findAuditLogs(filter2);
        assertEquals(1, result2.totalCount());
        assertEquals("categories", result2.items().get(0).tableName());

        AuditLogFilter filter3 = new AuditLogFilter(null, 10L, 1L, null, null, null, null, "id", "ASC", 1, 10);
        PagedResult<AuditLog> result3 = repository.findAuditLogs(filter3);
        assertEquals(2, result3.totalCount());
    }

    @Test
    void findAuditLogs_pagination_worksCorrectly() {
        for (int i = 0; i < 5; i++) {
            repository.insertAuditLog(new AuditLog(null, 1L, "UPDATE", "products", (long) i, null, null, null, null, LocalDateTime.now()));
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, null, "id", "ASC", 1, 2);
        PagedResult<AuditLog> page1 = repository.findAuditLogs(filter);
        assertEquals(5, page1.totalCount());
        assertEquals(3, page1.totalPages());
        assertEquals(2, page1.items().size());
        assertEquals(1, page1.page());

        AuditLogFilter filterPage2 = new AuditLogFilter(null, null, null, null, null, null, null, "id", "ASC", 2, 2);
        PagedResult<AuditLog> page2 = repository.findAuditLogs(filterPage2);
        assertEquals(2, page2.items().size());
        assertEquals(2, page2.page());
    }

    @Test
    void findAuditLogs_searchTerm_filtersCorrectly() {
        repository.insertAuditLog(new AuditLog(null, 1L, "CREATE", "products", 10L, null, null, null, null, LocalDateTime.now()));
        repository.insertAuditLog(new AuditLog(null, 1L, "DELETE", "categories", 5L, null, null, null, null, LocalDateTime.now()));

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "products", "id", "ASC", 1, 10);
        PagedResult<AuditLog> result = repository.findAuditLogs(filter);
        assertEquals(1, result.totalCount());
        assertEquals("CREATE", result.items().get(0).action());
    }

    @Test
    void insertAuditLog_exactlyAtLimit_noCleanup() {
        for (int i = 0; i < 1000; i++) {
            repository.insertAuditLog(new AuditLog(null, 1L, "ACTION", "TABLE", (long)i, null, null, null, null, LocalDateTime.now()));
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, null, "id", "ASC", 1, 1);
        PagedResult<AuditLog> result = repository.findAuditLogs(filter);
        assertEquals(1000, result.totalCount());

        assertNotNull(repository.findAuditLogById(1L));
    }

    @Test
    void insertAuditLog_oneOverLimit_removesFirst() {
        for (int i = 0; i < 1001; i++) {
            repository.insertAuditLog(new AuditLog(null, 1L, "ACTION", "TABLE", (long)i, null, null, null, null, LocalDateTime.now()));
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, null, "id", "ASC", 1, 1);
        PagedResult<AuditLog> result = repository.findAuditLogs(filter);
        assertEquals(1000, result.totalCount());
        assertNull(repository.findAuditLogById(1L));
        assertNotNull(repository.findAuditLogById(2L));
    }

    @Test
    void insertAuditLog_massiveInsertion_maintainsLimit() {
        for (int i = 0; i < 1100; i++) {
            repository.insertAuditLog(new AuditLog(null, 1L, "ACTION", "TABLE", (long)i, null, null, null, null, LocalDateTime.now()));
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, null, "id", "ASC", 1, 1);
        PagedResult<AuditLog> result = repository.findAuditLogs(filter);
        assertEquals(1000, result.totalCount());
        
        // The last 1000 should be from 101 to 1100
        assertNull(repository.findAuditLogById(100L));
        assertNotNull(repository.findAuditLogById(101L));
        assertNotNull(repository.findAuditLogById(1100L));
    }
}

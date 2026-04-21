package com.picopossum.persistence.repositories.sqlite;

import com.picopossum.domain.model.AuditLog;
import com.picopossum.shared.dto.AuditLogFilter;
import com.picopossum.shared.dto.PagedResult;
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
            CREATE TABLE audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                table_name TEXT NOT NULL,
                row_id INTEGER,
                old_data TEXT,
                new_data TEXT,
                event_details TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """);
    }

    @Test
    void insertAuditLog_validLog_insertsSuccessfully() {
        AuditLog log = new AuditLog(null, "UPDATE", "products", 100L, "{}", "{}", "Product updated", "info", LocalDateTime.now());
        long id = repository.insertAuditLog(log);
        assertTrue(id > 0);
    }

    @Test
    void findAuditLogById_found_returnsLog() {
        AuditLog log = new AuditLog(null, "CREATE", "products", 10L, null, "{}", "A", "info", LocalDateTime.now());
        long id = repository.insertAuditLog(log);

        AuditLog result = repository.findAuditLogById(id);
        assertNotNull(result);
        assertEquals("CREATE", result.action());
        assertEquals("products", result.tableName());
        assertEquals(10L, result.rowId());
    }

    @Test
    void findAuditLogs_withFilters_returnsCorrectResults() {
        repository.insertAuditLog(new AuditLog(null, "CREATE", "products", 10L, null, "{}", "A", "info", LocalDateTime.now()));
        repository.insertAuditLog(new AuditLog(null, "UPDATE", "products", 10L, "{}", "{}", "B", "info", LocalDateTime.now()));
        repository.insertAuditLog(new AuditLog(null, "DELETE", "categories", 5L, "{}", null, "C", "info", LocalDateTime.now()));

        AuditLogFilter filter1 = new AuditLogFilter("products", null, null, null, null, null, "id", "ASC", 1, 10);
        PagedResult<AuditLog> result1 = repository.findAuditLogs(filter1);
        assertEquals(2, result1.totalCount());

        AuditLogFilter filter2 = new AuditLogFilter(null, null, List.of("DELETE"), null, null, null, "id", "ASC", 1, 10);
        PagedResult<AuditLog> result2 = repository.findAuditLogs(filter2);
        assertEquals(1, result2.totalCount());
        assertEquals("categories", result2.items().get(0).tableName());
    }

    @Test
    void findAuditLogs_pagination_worksCorrectly() {
        for (int i = 0; i < 5; i++) {
            repository.insertAuditLog(new AuditLog(null, "UPDATE", "products", (long) i, null, null, null, "info", LocalDateTime.now()));
        }

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "id", "ASC", 1, 2);
        PagedResult<AuditLog> page1 = repository.findAuditLogs(filter);
        assertEquals(5, page1.totalCount());
        assertEquals(3, page1.totalPages());
        assertEquals(2, page1.items().size());

        AuditLogFilter filterPage2 = new AuditLogFilter(null, null, null, null, null, null, "id", "ASC", 2, 2);
        PagedResult<AuditLog> page2 = repository.findAuditLogs(filterPage2);
        assertEquals(2, page2.items().size());
        assertEquals(2, page2.page());
    }

    @Test
    void insertAuditLog_oneOverLimit_removesFirst() {
        for (int i = 0; i < 1001; i++) {
            repository.insertAuditLog(new AuditLog(null, "ACTION", "TABLE", (long)i, null, null, null, "info", LocalDateTime.now()));
        }
        repository.cleanupOldLogs();

        AuditLogFilter filter = new AuditLogFilter(null, null, null, null, null, null, "id", "ASC", 1, 1);
        PagedResult<AuditLog> result = repository.findAuditLogs(filter);
        assertEquals(1000, result.totalCount());
        assertNull(repository.findAuditLogById(1L));
        assertNotNull(repository.findAuditLogById(2L));
    }
}

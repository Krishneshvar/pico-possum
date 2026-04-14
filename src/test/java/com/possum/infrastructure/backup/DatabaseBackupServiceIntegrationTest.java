package com.possum.infrastructure.backup;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import com.possum.persistence.repositories.sqlite.SqliteCategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupServiceIntegrationTest {

    private AppPaths appPaths;
    private DatabaseManager databaseManager;
    private DatabaseBackupService backupService;
    private SqliteCategoryRepository categoryRepository;

    @AfterEach
    void tearDown() throws IOException {
        if (backupService != null) {
            backupService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (appPaths != null) {
            deleteDirectory(appPaths.getAppRoot());
        }
    }

    @Test
    void shouldCreateAndRestoreBackupSuccessfully() {
        initializeFreshDatabase();

        String preservedCategory = "Category-Keep-" + UUID.randomUUID();
        String transientCategory = "Category-Transient-" + UUID.randomUUID();

        categoryRepository.insertCategory(preservedCategory, null);
        Path backup = backupService.createManualBackup();
        assertTrue(Files.exists(backup));

        categoryRepository.insertCategory(transientCategory, null);
        assertEquals(1, countCategoriesByName(transientCategory));

        DatabaseBackupService.RestoreResult result = backupService.restoreFromBackup(backup);

        assertNotNull(result);
        assertTrue(Files.exists(result.preRestoreBackup()));
        assertEquals(1, countCategoriesByName(preservedCategory));
        assertEquals(0, countCategoriesByName(transientCategory));
    }

    @Test
    void shouldCreateOnlyOneAutoBackupPerDay() {
        initializeFreshDatabase();

        Path firstBackup = backupService.runDailyBackupIfDue();
        Path secondBackup = backupService.runDailyBackupIfDue();

        assertTrue(Files.exists(firstBackup));
        assertEquals(firstBackup, secondBackup);
        assertTrue(firstBackup.getFileName().toString().startsWith("possum-auto-backup-"));
    }

    private void initializeFreshDatabase() {
        appPaths = new AppPaths("possum-backup-test-" + UUID.randomUUID());
        databaseManager = new DatabaseManager(appPaths);
        databaseManager.initialize();

        backupService = new DatabaseBackupService(appPaths, databaseManager);
        categoryRepository = new SqliteCategoryRepository(databaseManager);
    }

    private int countCategoriesByName(String name) {
        final String sql = "SELECT COUNT(*) FROM categories WHERE name = ?";
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query categories", ex);
        }
        return 0;
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete test artifact: " + path, ex);
                        }
                    });
        }
    }
}

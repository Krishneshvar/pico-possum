package com.possum.infrastructure.backup;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseBackupServiceTest {

    @TempDir
    Path tempDir;

    private AppPaths appPaths;
    private DatabaseManager databaseManager;
    private DatabaseBackupService backupService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() throws IOException {
        appPaths = mock(AppPaths.class);
        databaseManager = mock(DatabaseManager.class);
        
        Path dbPath = tempDir.resolve("possum.db");
        Path backupsDir = tempDir.resolve("backups");
        Files.createDirectories(backupsDir);
        Files.createFile(dbPath);
        
        when(appPaths.getDatabasePath()).thenReturn(dbPath);
        when(appPaths.getBackupsDir()).thenReturn(backupsDir);
        
        fixedClock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.systemDefault());
        backupService = new DatabaseBackupService(appPaths, databaseManager, fixedClock);
    }

    @AfterEach
    void tearDown() {
        if (backupService != null) {
            backupService.close();
        }
    }

    @Test
    void createManualBackup_shouldCreateBackupFile() {
        Path backup = backupService.createManualBackup();
        
        assertNotNull(backup);
        assertTrue(Files.exists(backup));
        assertTrue(backup.getFileName().toString().startsWith("possum-manual-backup-"));
        assertTrue(backup.getFileName().toString().endsWith(".db"));
    }

    @Test
    void runDailyBackupIfDue_shouldCreateBackupOnFirstRun() {
        Path backup = backupService.runDailyBackupIfDue();
        
        assertNotNull(backup);
        assertTrue(Files.exists(backup));
        assertTrue(backup.getFileName().toString().startsWith("possum-auto-backup-"));
    }

    @Test
    void runDailyBackupIfDue_shouldSkipIfBackupExistsForToday() {
        Path firstBackup = backupService.runDailyBackupIfDue();
        Path secondBackup = backupService.runDailyBackupIfDue();
        
        assertEquals(firstBackup, secondBackup);
    }

    @Test
    void restoreFromBackup_shouldThrowIfBackupFileDoesNotExist() {
        Path nonExistent = tempDir.resolve("nonexistent.db");
        
        assertThrows(IllegalArgumentException.class, 
            () -> backupService.restoreFromBackup(nonExistent));
    }

    @Test
    void restoreFromBackup_shouldThrowIfBackupFileIsNotDb() throws IOException {
        Path txtFile = tempDir.resolve("backup.txt");
        Files.createFile(txtFile);
        
        assertThrows(IllegalArgumentException.class, 
            () -> backupService.restoreFromBackup(txtFile));
    }

    @Test
    void restoreFromBackup_shouldThrowIfBackupFileIsLiveDatabase() {
        Path dbPath = appPaths.getDatabasePath();
        
        assertThrows(IllegalArgumentException.class, 
            () -> backupService.restoreFromBackup(dbPath));
    }

    @Test
    void findLatestBackup_shouldReturnEmptyWhenNoBackups() {
        Optional<Path> latest = backupService.findLatestBackup();
        
        assertTrue(latest.isEmpty());
    }

    @Test
    void findLatestBackup_shouldReturnMostRecentBackup() throws IOException, InterruptedException {
        Path backup1 = backupService.createManualBackup();
        Thread.sleep(10);
        try (DatabaseBackupService backupService2 = new DatabaseBackupService(appPaths, databaseManager, Clock.fixed(Instant.parse("2024-01-15T10:00:01Z"), ZoneId.systemDefault()))) {
            Path backup2 = backupService2.createManualBackup();
            
            Optional<Path> latest = backupService.findLatestBackup();
            
            assertTrue(latest.isPresent());
            assertEquals(backup2, latest.get());
        }
    }

    @Test
    void listBackups_shouldReturnEmptyListWhenNoBackups() {
        List<Path> backups = backupService.listBackups();
        
        assertNotNull(backups);
        assertTrue(backups.isEmpty());
    }

    @Test
    void listBackups_shouldReturnAllBackupsSortedByDate() throws InterruptedException {
        Path backup1 = backupService.createManualBackup();
        Thread.sleep(10);
        try (DatabaseBackupService backupService2 = new DatabaseBackupService(appPaths, databaseManager, Clock.fixed(Instant.parse("2024-01-15T10:00:01Z"), ZoneId.systemDefault()))) {
            Path backup2 = backupService2.createManualBackup();
            
            List<Path> backups = backupService.listBackups();
            
            assertEquals(2, backups.size());
            assertEquals(backup2, backups.get(0)); // Most recent first
            assertEquals(backup1, backups.get(1));
        }
    }

    @Test
    void getBackupsDirectory_shouldReturnCorrectPath() {
        Path backupsDir = backupService.getBackupsDirectory();
        
        assertNotNull(backupsDir);
        assertEquals(appPaths.getBackupsDir().toAbsolutePath(), backupsDir);
    }

    @Test
    void startDailyBackups_shouldNotThrow() {
        assertDoesNotThrow(() -> backupService.startDailyBackups());
    }

    @Test
    void startDailyBackups_shouldBeIdempotent() {
        backupService.startDailyBackups();
        assertDoesNotThrow(() -> backupService.startDailyBackups());
    }

    @Test
    void stopDailyBackups_shouldNotThrow() {
        backupService.startDailyBackups();
        assertDoesNotThrow(() -> backupService.stopDailyBackups());
    }

    @Test
    void constructor_shouldThrowIfAppPathsIsNull() {
        assertThrows(NullPointerException.class, 
            () -> new DatabaseBackupService(null, databaseManager));
    }

    @Test
    void constructor_shouldThrowIfDatabaseManagerIsNull() {
        assertThrows(NullPointerException.class, 
            () -> new DatabaseBackupService(appPaths, null));
    }
}

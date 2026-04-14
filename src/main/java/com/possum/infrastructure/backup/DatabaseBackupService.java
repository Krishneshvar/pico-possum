package com.possum.infrastructure.backup;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.persistence.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class DatabaseBackupService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseBackupService.class);

    private static final String FILE_EXTENSION = ".db";
    private static final String AUTO_BACKUP_PREFIX = "possum-auto-backup-";
    private static final String MANUAL_BACKUP_PREFIX = "possum-manual-backup-";
    private static final String PRE_RESTORE_PREFIX = "possum-pre-restore-backup-";

    private static final DateTimeFormatter AUTO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final int AUTO_BACKUP_RETENTION_DAYS = 30;

    private final AppPaths appPaths;
    private final DatabaseManager databaseManager;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ReentrantLock operationLock = new ReentrantLock(true);

    private volatile boolean schedulerStarted;

    public DatabaseBackupService(AppPaths appPaths, DatabaseManager databaseManager) {
        this(appPaths, databaseManager, Clock.systemDefaultZone());
    }

    DatabaseBackupService(AppPaths appPaths, DatabaseManager databaseManager, Clock clock) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "database-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        };
        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public synchronized void startDailyBackups() {
        if (schedulerStarted) {
            return;
        }

        schedulerStarted = true;
        scheduler.execute(this::runDailyBackupIfDueSafely);
        scheduler.scheduleWithFixedDelay(this::runDailyBackupIfDueSafely, 1, 1, TimeUnit.HOURS);
        LOGGER.info("Daily database backup scheduler started");
    }

    public synchronized void stopDailyBackups() {
        schedulerStarted = false;
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warn("Database backup scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Daily database backup scheduler stopped");
    }

    public Path createManualBackup() {
        operationLock.lock();
        try {
            Path backupPath = getBackupsDirectory().resolve(buildTimestampedName(MANUAL_BACKUP_PREFIX));
            createSnapshotAt(backupPath);
            LOGGER.info("Manual backup created at {}", backupPath);
            return backupPath;
        } finally {
            operationLock.unlock();
        }
    }

    public Path runDailyBackupIfDue() {
        operationLock.lock();
        try {
            LocalDate today = LocalDate.now(clock);
            Path target = getBackupsDirectory().resolve(buildDailyName(today));
            if (Files.exists(target)) {
                return target;
            }

            createSnapshotAt(target);
            pruneOldAutoBackups(today);
            LOGGER.info("Daily auto-backup created at {}", target);
            return target;
        } finally {
            operationLock.unlock();
        }
    }

    public RestoreResult restoreFromBackup(Path backupFile) {
        operationLock.lock();
        try {
            Path source = normalizeAndValidateBackupFile(backupFile);
            Path databasePath = appPaths.getDatabasePath().toAbsolutePath();

            if (source.equals(databasePath)) {
                throw new IllegalArgumentException("Selected file is the live database. Choose a backup file instead.");
            }

            validateSqliteFile(source);

            Path preRestoreBackup = getBackupsDirectory().resolve(buildTimestampedName(PRE_RESTORE_PREFIX));
            createSnapshotAt(preRestoreBackup);

            Path candidate = Files.createTempFile(databasePath.getParent(), "restore-candidate-", FILE_EXTENSION);
            try {
                Files.copy(source, candidate, StandardCopyOption.REPLACE_EXISTING);
                replaceLiveDatabase(candidate, databasePath, preRestoreBackup);
                LOGGER.info("Database restored successfully from {}", source);
                return new RestoreResult(source, preRestoreBackup, Instant.now(clock));
            } finally {
                Files.deleteIfExists(candidate);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to restore database backup", ex);
        } finally {
            operationLock.unlock();
        }
    }

    public Optional<Path> findLatestBackup() {
        try (Stream<Path> files = Files.list(getBackupsDirectory())) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isBackupFile)
                    .max(Comparator.comparing(this::safeLastModifiedMillis));
        } catch (IOException ex) {
            LOGGER.warn("Failed to read backup directory", ex);
            return Optional.empty();
        }
    }

    public List<Path> listBackups() {
        try (Stream<Path> files = Files.list(getBackupsDirectory())) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(this::isBackupFile)
                    .sorted(Comparator.comparing(this::safeLastModifiedMillis).reversed())
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list backups", ex);
        }
    }

    public Path getBackupsDirectory() {
        return appPaths.getBackupsDir().toAbsolutePath();
    }

    @Override
    public void close() {
        stopDailyBackups();
    }

    private void runDailyBackupIfDueSafely() {
        try {
            runDailyBackupIfDue();
        } catch (Exception ex) {
            LOGGER.error("Automatic daily backup failed", ex);
        }
    }

    private Path normalizeAndValidateBackupFile(Path backupFile) {
        Objects.requireNonNull(backupFile, "backupFile must not be null");
        Path path = backupFile.toAbsolutePath().normalize();

        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Backup file does not exist: " + path);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Backup file is not readable: " + path);
        }
        if (!isBackupFile(path)) {
            throw new IllegalArgumentException("Backup file must be a .db file");
        }
        return path;
    }

    private void replaceLiveDatabase(Path candidateBackup, Path databasePath, Path rollbackBackup) throws IOException {
        databaseManager.close();
        try {
            deleteSqliteSidecarFiles(databasePath);
            Files.deleteIfExists(databasePath);
            moveReplacing(candidateBackup, databasePath);
            databaseManager.initialize();
        } catch (Exception ex) {
            LOGGER.error("Restore failed while replacing live database", ex);
            recoverFromRollbackBackup(databasePath, rollbackBackup, ex);
            throw new IllegalStateException("Restore failed while replacing the live database", ex);
        }
    }

    private void recoverFromRollbackBackup(Path databasePath, Path rollbackBackup, Exception primaryFailure) {
        try {
            deleteSqliteSidecarFiles(databasePath);
            Files.deleteIfExists(databasePath);
            Files.copy(rollbackBackup, databasePath, StandardCopyOption.REPLACE_EXISTING);
            databaseManager.initialize();
            LOGGER.info("Database recovered from rollback backup {}", rollbackBackup);
        } catch (Exception recoveryFailure) {
            primaryFailure.addSuppressed(recoveryFailure);
            LOGGER.error("Failed to recover database from rollback backup {}", rollbackBackup, recoveryFailure);
        }
    }

    private void createSnapshotAt(Path targetPath) {
        Objects.requireNonNull(targetPath, "targetPath must not be null");

        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create backup directory", ex);
        }

        Path tempTarget = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(tempTarget);
            runVacuumInto(tempTarget);
            moveReplacing(tempTarget, targetPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create database backup at " + targetPath, ex);
        } finally {
            try {
                Files.deleteIfExists(tempTarget);
            } catch (IOException ignored) {
                // best effort cleanup
            }
        }
    }

    private void runVacuumInto(Path targetPath) {
        String sql = "VACUUM INTO '" + escapeSqlLiteral(targetPath.toAbsolutePath().toString()) + "'";

        try (Connection connection = openMaintenanceConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("SQLite backup command failed", ex);
        }
    }

    private Connection openMaintenanceConnection() throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + appPaths.getDatabasePath().toAbsolutePath();
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(10_000);

        Connection connection = DriverManager.getConnection(jdbcUrl, config.toProperties());
        connection.setAutoCommit(true);
        return connection;
    }

    private void validateSqliteFile(Path backupFile) {
        String jdbcUrl = "jdbc:sqlite:" + backupFile.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA schema_version");
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Selected file is not a valid SQLite backup", ex);
        }
    }

    private void pruneOldAutoBackups(LocalDate today) {
        try (Stream<Path> files = Files.list(getBackupsDirectory())) {
            files.filter(Files::isRegularFile)
                 .filter(this::isAutoBackupFile)
                 .forEach(path -> {
                     Optional<LocalDate> date = extractAutoBackupDate(path.getFileName().toString());
                     if (date.isPresent() && date.get().isBefore(today.minusDays(AUTO_BACKUP_RETENTION_DAYS))) {
                         try {
                             Files.deleteIfExists(path);
                             LOGGER.info("Pruned old auto-backup: {}", path);
                         } catch (IOException ex) {
                             LOGGER.warn("Failed to prune old auto-backup: {}", path, ex);
                         }
                     }
                 });
        } catch (IOException ex) {
            LOGGER.warn("Failed to prune old backups", ex);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteSqliteSidecarFiles(Path databasePath) throws IOException {
        Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-wal"));
        Files.deleteIfExists(databasePath.resolveSibling(databasePath.getFileName() + "-shm"));
    }

    private String buildDailyName(LocalDate date) {
        return AUTO_BACKUP_PREFIX + AUTO_DATE_FORMAT.format(date) + FILE_EXTENSION;
    }

    private String buildTimestampedName(String prefix) {
        return prefix + TIMESTAMP_FORMAT.format(LocalDateTime.now(clock)) + FILE_EXTENSION;
    }

    private boolean isAutoBackupFile(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(FILE_EXTENSION);
    }

    private boolean isBackupFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(FILE_EXTENSION);
    }

    private Optional<LocalDate> extractAutoBackupDate(String filename) {
        if (!filename.startsWith(AUTO_BACKUP_PREFIX) || !filename.endsWith(FILE_EXTENSION)) {
            return Optional.empty();
        }

        String datePart = filename.substring(AUTO_BACKUP_PREFIX.length(), filename.length() - FILE_EXTENSION.length());
        try {
            return Optional.of(LocalDate.parse(datePart, AUTO_DATE_FORMAT));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private long safeLastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            return 0;
        }
    }

    private static String escapeSqlLiteral(String input) {
        return input.replace("'", "''");
    }

    public record RestoreResult(Path sourceBackup, Path preRestoreBackup, Instant restoredAt) {
    }
}

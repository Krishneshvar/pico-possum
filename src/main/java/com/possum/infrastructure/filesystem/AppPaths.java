package com.possum.infrastructure.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AppPaths {

    private static final String DEFAULT_APP_DIRECTORY = "possum";
    private static final String DATABASE_FILENAME = "possum.db";

    private final Path appDataRoot;

    public AppPaths() {
        this(DEFAULT_APP_DIRECTORY);
    }

    public AppPaths(String appDirectory) {
        this.appDataRoot = resolveAppDataRoot(appDirectory);
    }

    public Path getDatabasePath() {
        Path databaseDirectory = ensureDirectory(appDataRoot.resolve("database"));
        return databaseDirectory.resolve(DATABASE_FILENAME);
    }

    public Path getAppRoot() {
        return ensureDirectory(appDataRoot);
    }

    public Path getUploadsDir() {
        return ensureDirectory(appDataRoot.resolve("uploads"));
    }

    public Path getLogsDir() {
        return ensureDirectory(appDataRoot.resolve("logs"));
    }

    public Path getTempDir() {
        return ensureDirectory(appDataRoot.resolve("temp"));
    }

    public Path getBackupsDir() {
        return ensureDirectory(appDataRoot.resolve("backups"));
    }

    public Path getSettingsDir() {
        return ensureDirectory(appDataRoot.resolve("settings"));
    }

    private static Path resolveAppDataRoot(String appDirectory) {
        String normalizedOs = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        Path baseDirectory;

        if (normalizedOs.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                baseDirectory = Path.of(appData);
            } else {
                baseDirectory = Path.of(System.getProperty("user.home"), "AppData", "Roaming");
            }
        } else if (normalizedOs.contains("mac")) {
            baseDirectory = Path.of(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            if (xdgDataHome != null && !xdgDataHome.isBlank()) {
                baseDirectory = Path.of(xdgDataHome);
            } else {
                baseDirectory = Path.of(System.getProperty("user.home"), ".local", "share");
            }
        }

        return baseDirectory.resolve(appDirectory);
    }

    private static Path ensureDirectory(Path directory) {
        try {
            if (Files.notExists(directory)) {
                Files.createDirectories(directory);
            }
            return directory;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create directory: " + directory, ex);
        }
    }
}

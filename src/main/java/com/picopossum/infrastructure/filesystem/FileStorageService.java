package com.picopossum.infrastructure.filesystem;

import com.picopossum.infrastructure.logging.LoggingConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Handles physical file storage operations, separating filesystem concerns from business logic.
 * Designed for production use in a standalone desktop application.
 */
public class FileStorageService {

    private final AppPaths appPaths;

    public FileStorageService(AppPaths appPaths) {
        this.appPaths = appPaths;
    }

    /**
     * Stores a file and returns its absolute path.
     * @param sourcePath Physical path of the file to store.
     * @param subDirectory Subdirectory within the app root (e.g., "products").
     * @return Path where the file is stored.
     * @throws IOException If storage fails.
     */
    public String store(String sourcePath, String subDirectory) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("Source file does not exist: " + sourcePath);
        }

        Path targetDir = appPaths.getAppRoot().resolve(subDirectory);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String extension = getFileExtension(sourcePath);
        String fileName = UUID.randomUUID().toString() + extension;
        Path targetPath = targetDir.resolve(fileName);

        Files.copy(source, targetPath);
        return targetPath.toString();
    }

    /**
     * Deletes a file from the filesystem.
     * @param pathString Absolute path of the file to delete.
     */
    public void delete(String pathString) {
        if (pathString == null || pathString.isBlank()) return;
        
        try {
            Path path = Paths.get(pathString);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            LoggingConfig.getLogger().error("Failed to delete file: {}", pathString, e);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex);
    }
}

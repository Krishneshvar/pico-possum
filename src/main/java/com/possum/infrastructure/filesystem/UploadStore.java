package com.possum.infrastructure.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class UploadStore {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private final AppPaths appPaths;

    public UploadStore(AppPaths appPaths) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
    }

    public String saveFile(Path sourceFile) throws IOException {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");

        if (Files.notExists(sourceFile)) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
        }

        String mimeType = Files.probeContentType(sourceFile);
        if (mimeType != null && !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new IllegalArgumentException("Invalid file type: " + mimeType);
        }

        String extension = getFileExtension(sourceFile);
        String uniqueFilename = UUID.randomUUID() + extension;
        Path targetPath = appPaths.getUploadsDir().resolve(uniqueFilename);

        Files.copy(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return uniqueFilename;
    }

    public void deleteFile(String filename) throws IOException {
        Objects.requireNonNull(filename, "filename must not be null");
        Path filePath = getFilePath(filename);
        Files.deleteIfExists(filePath);
    }

    public Path getFilePath(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");
        return appPaths.getUploadsDir().resolve(filename);
    }

    private String getFileExtension(Path file) {
        String filename = file.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex) : "";
    }
}

package com.possum.infrastructure.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class UploadStoreTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppPaths appPaths;

    private UploadStore uploadStore;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        Path uploadsDir = tempDir.resolve("uploads");
        Files.createDirectories(uploadsDir);
        when(appPaths.getUploadsDir()).thenReturn(uploadsDir);
        
        uploadStore = new UploadStore(appPaths);
    }

    @Test
    void saveFile_validImage_returnsUniqueFilename() throws IOException {
        Path source = tempDir.resolve("image.jpg");
        Files.writeString(source, "image-content");
        
        // Note: probeContentType might be null in some environments for .jpg during tests
        // But we check that it doesn't fail if mime is allowed or unknown
        String result = uploadStore.saveFile(source);
        
        assertNotNull(result);
        assertTrue(result.endsWith(".jpg"));
        assertTrue(Files.exists(uploadStore.getFilePath(result)));
    }

    @Test
    void saveFile_sourceNotExists_throwsException() {
        Path missing = tempDir.resolve("missing.png");
        assertThrows(IllegalArgumentException.class, () -> uploadStore.saveFile(missing));
    }

    @Test
    void deleteFile_existingFile_removesFromDisk() throws IOException {
        Path source = tempDir.resolve("delete-me.png");
        Files.writeString(source, "content");
        String filename = uploadStore.saveFile(source);
        
        assertTrue(Files.exists(uploadStore.getFilePath(filename)));
        
        uploadStore.deleteFile(filename);
        assertFalse(Files.exists(uploadStore.getFilePath(filename)));
    }

    @Test
    void deleteFile_nonExistent_doesNotThrow() throws IOException {
        assertDoesNotThrow(() -> uploadStore.deleteFile("ghost.png"));
    }

    @Test
    void getFilePath_returnsCorrectPath() {
        Path path = uploadStore.getFilePath("test.jpg");
        assertEquals(appPaths.getUploadsDir().resolve("test.jpg"), path);
    }

    @Test
    void saveFile_nullSource_throwsException() {
        assertThrows(NullPointerException.class, () -> uploadStore.saveFile(null));
    }
}

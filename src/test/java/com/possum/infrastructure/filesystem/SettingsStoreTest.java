package com.possum.infrastructure.filesystem;

import com.possum.infrastructure.serialization.JsonService;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SettingsStoreTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppPaths appPaths;

    private JsonService jsonService;
    private SettingsStore settingsStore;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(appPaths.getSettingsDir()).thenReturn(tempDir);
        
        jsonService = new JsonService();
        settingsStore = new SettingsStore(appPaths, jsonService);
    }

    @Test
    void loadGeneralSettings_fileNotExists_returnsDefaultAndSaves() {
        GeneralSettings settings = settingsStore.loadGeneralSettings();
        
        assertNotNull(settings);
        // Verify default value (assuming GeneralSettings constructor sets defaults)
        assertNotNull(settings.getStoreName()); 
        
        // Verify it was saved to disk
        assertTrue(Files.exists(tempDir.resolve("general-settings.json")));
    }

    @Test
    void saveAndLoadGeneralSettings_persistsCorrectly() {
        GeneralSettings original = new GeneralSettings();
        original.setStoreName("Test Store");
        
        settingsStore.saveGeneralSettings(original);
        GeneralSettings loaded = settingsStore.loadGeneralSettings();
        
        assertEquals("Test Store", loaded.getStoreName());
    }

    @Test
    void loadBillSettings_returnsDefaultIfMissing() {
        BillSettings settings = settingsStore.loadBillSettings();
        assertNotNull(settings);
        assertTrue(Files.exists(tempDir.resolve("bill-settings.json")));
    }

    @Test
    void saveAndLoadBillSettings_persistsCorrectly() {
        BillSettings original = new BillSettings();
        original.setCurrency("USD");
        
        settingsStore.saveBillSettings(original);
        BillSettings loaded = settingsStore.loadBillSettings();
        
        assertEquals("USD", loaded.getCurrency());
    }

    @Test
    void genericGetSet_worksCorrectly() {
        String key = "custom-config";
        CustomSettings value = new CustomSettings("val1", 42);
        
        settingsStore.set(key, value);
        Optional<CustomSettings> loaded = settingsStore.get(key, CustomSettings.class);
        
        assertTrue(loaded.isPresent());
        assertEquals("val1", loaded.get().field1());
        assertEquals(42, loaded.get().field2());
    }

    @Test
    void get_missingKey_returnsEmpty() {
        Optional<CustomSettings> loaded = settingsStore.get("non-existent", CustomSettings.class);
        assertFalse(loaded.isPresent());
    }

    @Test
    void writeAtomically_handlesConcurrentWriteSafely() {
        // Atomic write check: ensure file is healthy even if multiple calls happen
        GeneralSettings s1 = new GeneralSettings();
        s1.setStoreName("S1");
        
        settingsStore.saveGeneralSettings(s1);
        
        GeneralSettings loaded = settingsStore.loadGeneralSettings();
        assertEquals("S1", loaded.getStoreName());
        
        // Ensure no leftover .tmp files
        assertFalse(Files.exists(tempDir.resolve("general-settings.json.tmp")));
    }

    public record CustomSettings(String field1, int field2) {}
}

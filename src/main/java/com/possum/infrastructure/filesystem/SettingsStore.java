package com.possum.infrastructure.filesystem;

import com.possum.infrastructure.serialization.JsonService;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class SettingsStore {

    private static final String GENERAL_SETTINGS_FILE = "general-settings.json";
    private static final String BILL_SETTINGS_FILE = "bill-settings.json";

    private final AppPaths appPaths;
    private final JsonService jsonService;

    public SettingsStore(AppPaths appPaths, JsonService jsonService) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
        this.jsonService = Objects.requireNonNull(jsonService, "jsonService must not be null");
    }

    public GeneralSettings loadGeneralSettings() {
        try {
            Path path = getGeneralSettingsPath();
            GeneralSettings settings = jsonService.read(path, GeneralSettings.class);
            if (settings == null) {
                settings = new GeneralSettings();
                saveGeneralSettings(settings);
            }
            return settings;
        } catch (com.possum.domain.exceptions.DataCorruptionException ex) {
            handleCorruption(getGeneralSettingsPath());
            GeneralSettings defaults = new GeneralSettings();
            saveGeneralSettings(defaults);
            return defaults;
        }
    }

    private void handleCorruption(Path corruptPath) {
        try {
            Path backup = corruptPath.resolveSibling(corruptPath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
            Files.move(corruptPath, backup);
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Self-Healing: Corrupt settings file relocated to {}", backup);
        } catch (Exception e) {
            com.possum.infrastructure.logging.LoggingConfig.getLogger().error("Self-Healing: Failed to move corrupt file {}", corruptPath);
        }
    }

    public void saveGeneralSettings(GeneralSettings settings) {
        writeAtomically(getGeneralSettingsPath(), settings);
    }

    public BillSettings loadBillSettings() {
        try {
            Path path = getBillSettingsPath();
            BillSettings settings = jsonService.read(path, BillSettings.class);
            if (settings == null) {
                settings = new BillSettings();
                saveBillSettings(settings);
            }
            return settings;
        } catch (com.possum.domain.exceptions.DataCorruptionException ex) {
            handleCorruption(getBillSettingsPath());
            BillSettings defaults = new BillSettings();
            saveBillSettings(defaults);
            return defaults;
        }
    }

    public void saveBillSettings(BillSettings settings) {
        writeAtomically(getBillSettingsPath(), settings);
    }

    public Path getSettingsDir() {
        return appPaths.getSettingsDir();
    }

    private Path getGeneralSettingsPath() {
        return getSettingsDir().resolve(GENERAL_SETTINGS_FILE);
    }

    private Path getBillSettingsPath() {
        return getSettingsDir().resolve(BILL_SETTINGS_FILE);
    }

    private void writeAtomically(Path targetPath, Object value) {
        Objects.requireNonNull(value, "settings value must not be null");

        Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        jsonService.write(tempPath, value);

        try {
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to atomically store settings: " + targetPath, ex);
        }
    }
    
    public <T> Optional<T> get(String key, Class<T> type) {
        Path path = getSettingsDir().resolve(key + ".json");
        T value = jsonService.read(path, type);
        return Optional.ofNullable(value);
    }
    
    public <T> void set(String key, T value) {
        Path path = getSettingsDir().resolve(key + ".json");
        writeAtomically(path, value);
    }
}

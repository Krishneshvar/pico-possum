package com.picopossum.infrastructure.filesystem;

import com.picopossum.infrastructure.serialization.JsonService;
import com.picopossum.shared.dto.BillSettings;
import com.picopossum.shared.dto.GeneralSettings;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SettingsStore {

    private static final String GENERAL_SETTINGS_KEY = "general-settings";
    private static final String BILL_SETTINGS_KEY = "bill-settings";
    
    private static final String GENERAL_SETTINGS_FILE = GENERAL_SETTINGS_KEY + ".json";
    private static final String BILL_SETTINGS_FILE = BILL_SETTINGS_KEY + ".json";

    private final AppPaths appPaths;
    private final JsonService jsonService;
    
    // In-memory cache for performance
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
    
    // Listener system for reactive updates
    private final CopyOnWriteArrayList<Consumer<String>> changeListeners = new CopyOnWriteArrayList<>();

    public SettingsStore(AppPaths appPaths, JsonService jsonService) {
        this.appPaths = Objects.requireNonNull(appPaths, "appPaths must not be null");
        this.jsonService = Objects.requireNonNull(jsonService, "jsonService must not be null");
    }

    public GeneralSettings loadGeneralSettings() {
        GeneralSettings cached = (GeneralSettings) cache.get(GENERAL_SETTINGS_KEY);
        if (cached != null) return cached;

        GeneralSettings settings = readFromFile(GENERAL_SETTINGS_KEY, GeneralSettings.class);
        if (settings == null) {
            settings = new GeneralSettings();
            saveGeneralSettings(settings);
        } else {
            cache.put(GENERAL_SETTINGS_KEY, settings);
        }
        return settings;
    }

    private <T> T readFromFile(String key, Class<T> clazz) {
        try {
            Path path = getSettingsDir().resolve(key + ".json");
            return jsonService.read(path, clazz);
        } catch (com.picopossum.domain.exceptions.DataCorruptionException ex) {
            handleCorruption(getSettingsDir().resolve(key + ".json"));
            return null;
        }
    }

    private void handleCorruption(Path corruptPath) {
        try {
            Path backup = corruptPath.resolveSibling(corruptPath.getFileName().toString() + ".corrupt." + System.currentTimeMillis());
            Files.move(corruptPath, backup);
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Self-Healing: Corrupt settings file relocated to {}", backup);
        } catch (Exception e) {
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Self-Healing: Failed to move corrupt file {}", corruptPath);
        }
    }

    public void saveGeneralSettings(GeneralSettings settings) {
        cache.put(GENERAL_SETTINGS_KEY, settings);
        writeAtomically(getGeneralSettingsPath(), settings);
        notifyChanged(GENERAL_SETTINGS_KEY);
    }

    public BillSettings loadBillSettings() {
        BillSettings cached = (BillSettings) cache.get(BILL_SETTINGS_KEY);
        if (cached != null) return cached;

        BillSettings settings = readFromFile(BILL_SETTINGS_KEY, BillSettings.class);
        if (settings == null) {
            settings = new BillSettings();
            saveBillSettings(settings);
        } else {
            cache.put(BILL_SETTINGS_KEY, settings);
        }
        return settings;
    }

    public void saveBillSettings(BillSettings settings) {
        cache.put(BILL_SETTINGS_KEY, settings);
        writeAtomically(getBillSettingsPath(), settings);
        notifyChanged(BILL_SETTINGS_KEY);
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
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        T cached = (T) cache.get(key);
        if (cached != null) return Optional.of(cached);

        Path path = getSettingsDir().resolve(key + ".json");
        T value = jsonService.read(path, type);
        if (value != null) {
            cache.put(key, value);
        }
        return Optional.ofNullable(value);
    }
    
    public <T> void set(String key, T value) {
        cache.put(key, value);
        Path path = getSettingsDir().resolve(key + ".json");
        writeAtomically(path, value);
        notifyChanged(key);
    }

    public void addChangeListener(Consumer<String> listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Consumer<String> listener) {
        changeListeners.remove(listener);
    }

    private void notifyChanged(String key) {
        for (Consumer<String> listener : changeListeners) {
            try {
                listener.accept(key);
            } catch (Exception e) {
                com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Error notifying settings listener for key: {}", key, e);
            }
        }
    }

    public void clearCache() {
        cache.clear();
    }
}

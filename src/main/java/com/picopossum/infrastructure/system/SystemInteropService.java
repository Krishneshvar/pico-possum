package com.picopossum.infrastructure.system;

import com.picopossum.infrastructure.logging.LoggingConfig;
import org.slf4j.Logger;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Service for OS-specific system interoperability.
 * Handles folder opening, file association, and platform-specific commands.
 */
public final class SystemInteropService {

    private static final Logger LOGGER = LoggingConfig.getLogger();

    /**
     * Opens the specified folder in the system's default file manager.
     * 
     * @param folder The path to the folder to open.
     * @return true if the folder was successfully opened, false otherwise.
     */
    public boolean openFolder(Path folder) {
        try {
            if (folder == null || !Files.exists(folder)) {
                LOGGER.warn("Attempted to open non-existent folder: {}", folder);
                return false;
            }

            folder = folder.toAbsolutePath().normalize();
            String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);

            // 1. Try Java AWT Desktop first
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(folder.toFile());
                    return true;
                }
            }

            // 2. OS-specific command fallbacks
            if (os.contains("win")) {
                return tryOpenWithCommand("explorer.exe", folder.toString());
            } else if (os.contains("mac")) {
                return tryOpenWithCommand("open", folder.toString());
            } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                // On Linux, try D-Bus first to talk directly to GUI file managers
                if (tryOpenWithDbus(folder)) {
                    return true;
                }
                // Try xdg-open with URI
                if (tryOpenWithCommand("xdg-open", folder.toUri().toString())) {
                    return true;
                }
                // Fallback to gio
                if (tryOpenWithCommand("gio", "open", folder.toUri().toString())) {
                    return true;
                }
            }

            // 3. Final fallback: browse()
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(folder.toUri());
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open folder: {}", folder, e);
        }
        return false;
    }

    private boolean tryOpenWithCommand(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (IOException e) {
            LOGGER.debug("Command failed: {} - {}", command[0], e.getMessage());
            return false;
        }
    }

    private boolean tryOpenWithDbus(Path folder) {
        try {
            // org.freedesktop.FileManager1.ShowItems is implemented by most GUI file managers
            new ProcessBuilder(
                    "dbus-send",
                    "--session",
                    "--dest=org.freedesktop.FileManager1",
                    "--type=method_call",
                    "/org/freedesktop/FileManager1",
                    "org.freedesktop.FileManager1.ShowItems",
                    "array:string:file://" + folder.toAbsolutePath().toString(),
                    "string:"
            ).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

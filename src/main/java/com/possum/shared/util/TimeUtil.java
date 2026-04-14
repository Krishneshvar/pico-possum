package com.possum.shared.util;

import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.shared.dto.GeneralSettings;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private static SettingsStore settingsStore;

    private TimeUtil() {
    }

    public static void initialize(SettingsStore store) {
        settingsStore = store;
    }

    /**
     * Get the current time in UTC, suitable for database storage.
     */
    public static LocalDateTime nowUTC() {
        return LocalDateTime.now(UTC);
    }

    /**
     * Convert a database LocalDateTime (assumed UTC) to the system's local timezone.
     */
    public static LocalDateTime toLocal(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }
        ZonedDateTime utcZoned = utcDateTime.atZone(UTC);
        return utcZoned.withZoneSameInstant(SYSTEM_ZONE).toLocalDateTime();
    }

    /**
     * Convert a local LocalDateTime to UTC for storage.
     */
    public static LocalDateTime toUTC(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        ZonedDateTime localZoned = localDateTime.atZone(SYSTEM_ZONE);
        return localZoned.withZoneSameInstant(UTC).toLocalDateTime();
    }

    /**
     * Use this pattern across the app for human-readable dates.
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Get the DateTimeFormatter based on the user's settings.
     */
    public static DateTimeFormatter getFormatter() {
        String dateFormat = "dd/MM/yyyy";
        String timeFormat = "hh:mm a";

        if (settingsStore != null) {
            GeneralSettings settings = settingsStore.loadGeneralSettings();

            if (settings != null) {
                switch (settings.getDateFormat()) {
                    case "MM/DD/YYYY":
                        dateFormat = "MM/dd/yyyy";
                        break;
                    case "YYYY/MM/DD":
                        dateFormat = "yyyy/MM/dd";
                        break;
                    case "Month Date, Year":
                        dateFormat = "MMMM dd, yyyy";
                        break;
                    case "Date Month, Year":
                        // Assuming "1st Aug, 2026" - let's use "dd MMM, yyyy" as standard format
                        dateFormat = "dd MMM, yyyy";
                        break;
                    case "DD/MM/YYYY":
                    default:
                        dateFormat = "dd/MM/yyyy";
                        break;
                }

                if ("24 hour format".equals(settings.getTimeFormat())) {
                    timeFormat = "HH:mm";
                }
            }
        }

        String pattern = dateFormat + " " + timeFormat;
        return DateTimeFormatter.ofPattern(pattern);
    }

    /**
     * Standard human-readable format.
     */
    public static String formatStandard(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(getFormatter());
    }

    /**
     * Get just the date formatter based on settings.
     */
    public static DateTimeFormatter getDateFormatter() {
        String dateFormat = "dd/MM/yyyy";

        if (settingsStore != null) {
            GeneralSettings settings = settingsStore.loadGeneralSettings();

            if (settings != null) {
                switch (settings.getDateFormat()) {
                    case "MM/DD/YYYY":
                        dateFormat = "MM/dd/yyyy";
                        break;
                    case "YYYY/MM/DD":
                        dateFormat = "yyyy/MM/dd";
                        break;
                    case "Month Date, Year":
                        dateFormat = "MMMM dd, yyyy";
                        break;
                    case "Date Month, Year":
                        dateFormat = "dd MMM, yyyy";
                        break;
                    case "DD/MM/YYYY":
                    default:
                        dateFormat = "dd/MM/yyyy";
                        break;
                }
            }
        }
        return DateTimeFormatter.ofPattern(dateFormat);
    }
}

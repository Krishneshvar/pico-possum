package com.possum.ui.common.controls;

import com.possum.shared.util.TimeUtil;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility for standardizing date control behavior across the application.
 */
public class DateControlUtils {

    /**
     * Applies the user's preferred date format to a DatePicker,
     * ensuring it displays and parses dates correctly according to settings.
     */
    public static void applyStandardFormat(DatePicker picker) {
        DateTimeFormatter formatter = TimeUtil.getDateFormatter();
        String pattern = TimeUtil.getDatePattern();

        picker.setConverter(new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                if (date != null) {
                    try {
                        return formatter.format(date);
                    } catch (Exception e) {
                        return "";
                    }
                }
                return "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    try {
                        return LocalDate.parse(string, formatter);
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
        });

        // Use a helpful prompt based on the pattern, made uppercase for readability (e.g., DD/MM/YYYY)
        picker.setPromptText(pattern.toUpperCase().replace('D', 'D').replace('Y', 'Y'));
    }
}

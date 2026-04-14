package com.possum.shared.util;

import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.shared.dto.GeneralSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeUtilTest {

    @Mock private SettingsStore settingsStore;

    @BeforeEach
    void setUp() {
        TimeUtil.initialize(settingsStore);
    }

    @Test
    @DisplayName("Should return nowUTC")
    void nowUTC_success() {
        LocalDateTime now = TimeUtil.nowUTC();
        assertNotNull(now);
    }

    @Test
    @DisplayName("Should convert between UTC and Local properly")
    void toLocalToUTC_success() {
        LocalDateTime local = LocalDateTime.of(2023, 1, 1, 12, 0);
        LocalDateTime utc = TimeUtil.toUTC(local);
        assertNotNull(utc);
        
        LocalDateTime backToLocal = TimeUtil.toLocal(utc);
        assertEquals(local, backToLocal);
    }

    @Test
    @DisplayName("Should handle nulls in date conversion")
    void convertNulls_success() {
        assertNull(TimeUtil.toLocal(null));
        assertNull(TimeUtil.toUTC(null));
    }

    @Test
    @DisplayName("Should get DateTimeFormatter based on settings")
    void getFormatter_withSettings_success() {
        GeneralSettings settings = new GeneralSettings();
        settings.setDateFormat("YYYY/MM/DD");
        settings.setTimeFormat("24 hour format");

        when(settingsStore.loadGeneralSettings()).thenReturn(settings);

        DateTimeFormatter formatter = TimeUtil.getFormatter();
        assertNotNull(formatter);
        
        LocalDateTime val = LocalDateTime.of(2023, 10, 15, 14, 30);
        String formatted = val.format(formatter);
        assertEquals("2023/10/15 14:30", formatted);
    }

    @Test
    @DisplayName("Should get just DateFormatter properly")
    void getDateFormatter_withSettings_success() {
        GeneralSettings settings = new GeneralSettings();
        settings.setDateFormat("Month Date, Year");

        when(settingsStore.loadGeneralSettings()).thenReturn(settings);

        DateTimeFormatter formatter = TimeUtil.getDateFormatter();
        assertNotNull(formatter);
        
        LocalDateTime val = LocalDateTime.of(2023, 10, 15, 0, 0);
        String formatted = val.format(formatter);
        assertEquals("October 15, 2023", formatted);
    }

    @Test
    @DisplayName("Should handle null settings for formatter")
    void getFormatter_nullSettings_success() {
        when(settingsStore.loadGeneralSettings()).thenReturn(null);
        
        DateTimeFormatter formatter = TimeUtil.getFormatter();
        assertNotNull(formatter);
    }

    @Test
    @DisplayName("Should format date strings using default pattern")
    void formatStandard_success() {
        // Just checking consistency
        LocalDateTime val = LocalDateTime.of(2023, 10, 15, 10, 0);
        String formatted = TimeUtil.formatStandard(val);
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
    }

    @Test
    @DisplayName("Should format with custom pattern")
    void format_customPattern_success() {
        LocalDateTime val = LocalDateTime.of(2023, 10, 15, 10, 0);
        String formatted = TimeUtil.format(val, "yyyy-MM-dd");
        assertEquals("2023-10-15", formatted);
    }
}

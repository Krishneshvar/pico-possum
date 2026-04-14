package com.possum.shared.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneralSettingsTest {

    @Test
    void defaultPrinterName_isEmptyByDefault() {
        GeneralSettings settings = new GeneralSettings();
        assertEquals("", settings.getDefaultPrinterName());
    }

    @Test
    void setDefaultPrinterName_trimsAndHandlesNull() {
        GeneralSettings settings = new GeneralSettings();

        settings.setDefaultPrinterName("  POS-THERMAL  ");
        assertEquals("POS-THERMAL", settings.getDefaultPrinterName());

        settings.setDefaultPrinterName(null);
        assertEquals("", settings.getDefaultPrinterName());
    }
}

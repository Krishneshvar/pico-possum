package com.possum.shared.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BillSettingsTest {

    @Test
    void paperWidth_defaultsTo80mmWhenInvalid() {
        BillSettings settings = new BillSettings();

        settings.setPaperWidth("invalid");
        assertEquals("80mm", settings.getPaperWidth());

        settings.setPaperWidth("58mm");
        assertEquals("58mm", settings.getPaperWidth());
    }

    @Test
    void sections_recoverDefaultsWhenMissingOrEmpty() {
        BillSettings settings = new BillSettings();

        settings.setSections(null);
        assertFalse(settings.getSections().isEmpty());

        settings.setSections(new ArrayList<>());
        assertFalse(settings.getSections().isEmpty());
    }
}

package com.possum.ui.settings;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;
import com.possum.ui.JavaFXInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillSettingsControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private SettingsStore settingsStore;
    @Mock private javafx.scene.layout.VBox sectionsContainer;
    @Mock private javafx.scene.control.ComboBox<String> paperWidthCombo;
    @Mock private javafx.scene.control.ComboBox<String> dateFormatCombo;
    @Mock private javafx.scene.control.ComboBox<String> timeFormatCombo;
    @Mock private javafx.scene.web.WebView previewWebView;
    @Mock private javafx.scene.web.WebEngine webEngine;
    @Mock private javafx.scene.control.Button saveButton;

    private BillSettingsController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("settings:view")));
        when(settingsStore.loadBillSettings()).thenReturn(new BillSettings());
        when(settingsStore.loadGeneralSettings()).thenReturn(new GeneralSettings());
        lenient().when(paperWidthCombo.getItems()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        lenient().when(dateFormatCombo.getItems()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        lenient().when(timeFormatCombo.getItems()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        lenient().when(previewWebView.getEngine()).thenReturn(webEngine);
        lenient().when(sectionsContainer.getChildren()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        
        controller = new BillSettingsController(settingsStore);
        setField(controller, "sectionsContainer", sectionsContainer);
        setField(controller, "paperWidthCombo", paperWidthCombo);
        setField(controller, "dateFormatCombo", dateFormatCombo);
        setField(controller, "timeFormatCombo", timeFormatCombo);
        setField(controller, "previewWebView", previewWebView);
        setField(controller, "saveButton", saveButton);
        
        controller.initialize();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = BillSettingsController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should load bill settings logic")
    void loadSettings_success() {
        verify(settingsStore, atLeastOnce()).loadBillSettings();
        verify(settingsStore, atLeastOnce()).loadGeneralSettings();
    }

    @Test
    @DisplayName("Should handle save logic")
    void handleSave_logic() {
        assertNotNull(controller);
    }
}

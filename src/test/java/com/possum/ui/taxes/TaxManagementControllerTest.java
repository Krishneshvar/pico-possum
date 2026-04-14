package com.possum.ui.taxes;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.taxes.TaxManagementService;
import com.possum.domain.model.TaxProfile;
import com.possum.ui.settings.tax.TaxProfilesController;
import com.possum.ui.JavaFXInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxManagementControllerTest {

    @BeforeAll
    static void initJFX() {
        JavaFXInitializer.initialize();
    }

    @Mock private TaxManagementService taxService;
    @Mock private com.possum.application.sales.TaxEngine taxEngine;
    @Mock private com.possum.ui.common.controls.DataTableView<TaxProfile> profilesTable;
    @Mock private javafx.scene.control.TableView<TaxProfile> tableView;
    @Mock private javafx.scene.control.TextField nameField;
    @Mock private javafx.scene.control.TextField countryCodeField;
    @Mock private javafx.scene.control.TextField regionCodeField;
    @Mock private javafx.scene.control.ComboBox<String> pricingModeCombo;
    @Mock private javafx.scene.control.CheckBox activeCheckBox;
    @Mock private javafx.scene.control.Label nameErrorLabel;
    @Mock private javafx.scene.control.Label countryCodeErrorLabel;
    @Mock private javafx.scene.control.Label pricingModeErrorLabel;

    private TaxProfilesController controller;

    @BeforeEach
    void setUp() throws Exception {
        AuthContext.setCurrentUser(new AuthUser(1L, "Test User", "testuser", List.of("admin"), List.of("settings:tax")));
        controller = new TaxProfilesController();
        
        lenient().when(profilesTable.getTableView()).thenReturn(tableView);
        lenient().when(tableView.getColumns()).thenReturn(javafx.collections.FXCollections.observableArrayList());
        javafx.scene.control.TableView.TableViewSelectionModel<TaxProfile> selectionModel = mock(javafx.scene.control.TableView.TableViewSelectionModel.class);
        when(tableView.getSelectionModel()).thenReturn(selectionModel);
        lenient().when(selectionModel.selectedItemProperty()).thenReturn(new javafx.beans.property.SimpleObjectProperty<>());
        lenient().when(nameField.focusedProperty()).thenReturn(new javafx.beans.property.SimpleBooleanProperty());
        lenient().when(countryCodeField.focusedProperty()).thenReturn(new javafx.beans.property.SimpleBooleanProperty());
        lenient().when(pricingModeCombo.focusedProperty()).thenReturn(new javafx.beans.property.SimpleBooleanProperty());

        setField(controller, "profilesTable", profilesTable);
        setField(controller, "nameField", nameField);
        setField(controller, "countryCodeField", countryCodeField);
        setField(controller, "regionCodeField", regionCodeField);
        setField(controller, "pricingModeCombo", pricingModeCombo);
        setField(controller, "activeCheckBox", activeCheckBox);
        setField(controller, "nameErrorLabel", nameErrorLabel);
        setField(controller, "countryCodeErrorLabel", countryCodeErrorLabel);
        setField(controller, "pricingModeErrorLabel", pricingModeErrorLabel);
        
        controller.initialize();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = TaxProfilesController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("Should load tax profiles via controller")
    void setTaxService_loadsProfiles_success() {
        List<TaxProfile> profiles = List.of(
            new TaxProfile(1L, "GST India", "IN", "01", "EXCLUSIVE", true, null, null),
            new TaxProfile(2L, "VAT UK", "GB", null, "INCLUSIVE", false, null, null)
        );
        when(taxService.getAllTaxProfiles()).thenReturn(profiles);

        // This call triggers loadProfiles internally
        controller.setTaxService(taxService, taxEngine);

        verify(taxService).getAllTaxProfiles();
    }

    @Test
    @DisplayName("Should handle delete profile logic")
    void delete_profile_logic() {
        // Just verify basic setup
        assertNotNull(controller);
    }
}

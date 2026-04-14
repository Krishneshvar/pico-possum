package com.possum.ui.taxes;

import com.possum.application.sales.TaxConfiguration;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.ui.common.toast.ToastService;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class TaxConfigurationController {
    
    @FXML private RadioButton invoiceLevelRadio;
    @FXML private RadioButton itemLevelRadio;
    @FXML private ToggleGroup roundingGroup;
    @FXML private CheckBox enableAuditCheckbox;
    @FXML private Button saveButton;
    @FXML private Label descriptionLabel;
    
    private final SettingsStore settingsStore;
    private final ToastService toastService;
    
    private static final String TAX_CONFIG_KEY = "tax.configuration";

    public TaxConfigurationController(SettingsStore settingsStore, ToastService toastService) {
        this.settingsStore = settingsStore;
        this.toastService = toastService;
    }

    @FXML
    public void initialize() {
        roundingGroup = new ToggleGroup();
        invoiceLevelRadio.setToggleGroup(roundingGroup);
        itemLevelRadio.setToggleGroup(roundingGroup);
        
        roundingGroup.selectedToggleProperty().addListener((obs, old, newVal) -> updateDescription());
        
        loadConfiguration();
    }

    private void loadConfiguration() {
        var config = settingsStore.get(TAX_CONFIG_KEY, TaxConfiguration.class)
            .orElse(TaxConfiguration.defaultConfig());
        
        if (config.roundingMode() == TaxConfiguration.RoundingMode.INVOICE_LEVEL) {
            invoiceLevelRadio.setSelected(true);
        } else {
            itemLevelRadio.setSelected(true);
        }
        
        enableAuditCheckbox.setSelected(config.enableAuditTrail());
        updateDescription();
    }

    private void updateDescription() {
        if (invoiceLevelRadio.isSelected()) {
            descriptionLabel.setText(
                "Invoice-level rounding: Tax is calculated on the sum of all items, then rounded once. " +
                "This is more accurate and preferred by most tax authorities."
            );
        } else {
            descriptionLabel.setText(
                "Item-level rounding: Tax is calculated and rounded for each item separately. " +
                "May result in small rounding differences at invoice level."
            );
        }
    }

    @FXML
    private void handleSave() {
        var roundingMode = invoiceLevelRadio.isSelected() 
            ? TaxConfiguration.RoundingMode.INVOICE_LEVEL 
            : TaxConfiguration.RoundingMode.ITEM_LEVEL;
        
        var config = new TaxConfiguration(roundingMode, enableAuditCheckbox.isSelected());
        settingsStore.set(TAX_CONFIG_KEY, config);
        
        toastService.success("Tax configuration saved successfully");
    }

    @FXML
    private void handleReset() {
        var config = TaxConfiguration.defaultConfig();
        settingsStore.set(TAX_CONFIG_KEY, config);
        loadConfiguration();
        toastService.info("Tax configuration reset to defaults");
    }
}

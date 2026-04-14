package com.possum.ui.settings.tax;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;

public class TaxManagementController {

    @FXML private TabPane taxTabPane;
    @FXML private TaxProfilesController taxProfilesController;
    @FXML private TaxCategoriesController taxCategoriesController;
    @FXML private TaxRulesController taxRulesController;
    @FXML private TaxSimulatorController taxSimulatorController;

    @FXML
    public void initialize() {
        // Controllers are automatically injected via fx:include
    }

    public void setServices(com.possum.application.taxes.TaxManagementService taxService, 
                            com.possum.domain.repositories.TaxRepository taxRepository,
                            com.possum.infrastructure.serialization.JsonService jsonService,
                            com.possum.application.sales.TaxEngine taxEngine) {
        if (taxProfilesController != null) taxProfilesController.setTaxService(taxService, taxEngine);
        if (taxCategoriesController != null) taxCategoriesController.setTaxService(taxService);
        if (taxRulesController != null) taxRulesController.setTaxService(taxService);
        if (taxSimulatorController != null) {
            taxSimulatorController.setTaxService(taxService);
            taxSimulatorController.setTaxRepository(taxRepository);
            taxSimulatorController.setJsonService(jsonService);
        }
    }
}

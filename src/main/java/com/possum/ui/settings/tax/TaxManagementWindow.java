package com.possum.ui.settings.tax;

import com.possum.application.taxes.TaxManagementService;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.infrastructure.serialization.JsonService;
import com.possum.domain.repositories.TaxRepository;
import com.possum.ui.common.controls.ViewStateEnhancer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TaxManagementWindow {

    public static void show(TaxRepository taxRepository, JsonService jsonService) {
        try {
            TaxManagementService taxService = new TaxManagementService(taxRepository);
            com.possum.application.sales.TaxEngine taxEngine =
                    new com.possum.application.sales.TaxEngine(taxRepository, jsonService);
            
            FXMLLoader loader = new FXMLLoader(
                TaxManagementWindow.class.getResource("/fxml/settings/tax/tax-management.fxml")
            );
            
            Parent root = loader.load();
            ViewStateEnhancer.enhance(root);
            
            TaxManagementController controller = loader.getController();
            controller.setServices(taxService, taxRepository, jsonService, taxEngine);
            
            Stage stage = new Stage();
            stage.setTitle("Tax Management");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 900, 700));
            stage.show();
            
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to open tax management window: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to open tax management window", e);
        }
    }
}

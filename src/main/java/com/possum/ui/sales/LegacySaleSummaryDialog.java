package com.possum.ui.sales;

import com.possum.domain.model.Sale;
import com.possum.shared.util.TimeUtil;
import com.possum.shared.util.CurrencyUtil;
import com.possum.ui.common.dialogs.DialogStyler;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class LegacySaleSummaryDialog {

    public static void show(Sale sale) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        DialogStyler.apply(alert);
        alert.setTitle("Legacy Bill Summary");
        alert.setHeaderText(null);

        VBox content = new VBox(15);
        content.setPadding(new Insets(10, 20, 10, 20));
        content.setMinWidth(420);

        VBox header = new VBox(5);
        Label titleLabel = new Label("Invoice #" + (sale.invoiceNumber() != null ? sale.invoiceNumber() : "-"));
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: -color-primary-dark;");
        Label subTitle = new Label("Legacy Row Summary");
        subTitle.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-text-muted; -fx-font-weight: 600; -fx-text-transform: uppercase;");
        header.getChildren().addAll(subTitle, titleLabel);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setPadding(new Insets(10, 0, 10, 0));
        
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(140);
        col1.setPrefWidth(140);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        addSummaryRow(grid, 0, "👤 Customer", sale.customerName() != null && !sale.customerName().isBlank() ? sale.customerName() : "Walk-in Customer");
        addSummaryRow(grid, 1, "📅 Date & Time", sale.saleDate() != null ? TimeUtil.formatStandard(TimeUtil.toLocal(sale.saleDate())) : "-");
        addSummaryRow(grid, 2, "💰 Net Amount", sale.totalAmount() != null ? CurrencyUtil.format(sale.totalAmount()) : CurrencyUtil.format(BigDecimal.ZERO));
        addSummaryRow(grid, 3, "💳 Payment", sale.paymentMethodName() != null ? sale.paymentMethodName() : "Unknown");

        VBox footer = new VBox(8);
        footer.setPadding(new Insets(15, 12, 12, 12));
        footer.setStyle("-fx-background-color: -color-info-bg; -fx-background-radius: 8; -fx-border-color: -color-info; -fx-border-width: 0 0 0 4;");
        
        Label infoIcon = new Label("ℹ Note");
        infoIcon.setStyle("-fx-font-weight: 800; -fx-text-fill: -color-info-text; -fx-font-size: 13px;");
        
        Label infoText = new Label("This data was imported from a legacy Serieswise CSV. Individual line-item details (products/quantities) are not available for this record.");
        infoText.setWrapText(true);
        infoText.setStyle("-fx-text-fill: -color-info-text; -fx-font-size: 13px; -fx-line-spacing: 1.2;");
        
        footer.getChildren().addAll(infoIcon, infoText);

        content.getChildren().addAll(header, new Separator(), grid, footer);
        alert.getDialogPane().setContent(content);
        alert.setContentText(null);
        alert.setGraphic(null);

        alert.showAndWait();
    }

    private static void addSummaryRow(GridPane grid, int row, String label, String value) {
        Label keyLabel = new Label(label);
        keyLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: -color-text-secondary; -fx-font-size: 14px;");
        keyLabel.setMinWidth(Region.USE_PREF_SIZE);
        
        Label valLabel = new Label(value);
        valLabel.setStyle("-fx-font-weight: 600; -fx-text-fill: -color-text-main; -fx-font-size: 14px;");
        valLabel.setWrapText(true);

        grid.add(keyLabel, 0, row);
        grid.add(valLabel, 1, row);
    }
}

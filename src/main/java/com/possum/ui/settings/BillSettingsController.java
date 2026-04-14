package com.possum.ui.settings;

import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.infrastructure.printing.BillRenderer;
import com.possum.shared.dto.BillSection;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;
import com.possum.ui.common.controls.NotificationService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BillSettingsController {

    @FXML private VBox sectionsContainer;
    @FXML private ComboBox<String> paperWidthCombo;
    @FXML private ComboBox<String> dateFormatCombo;
    @FXML private ComboBox<String> timeFormatCombo;

    @FXML private WebView previewWebView;
    @FXML private Button saveButton;

    private SettingsStore settingsStore;
    private BillSettings billSettings;
    private GeneralSettings generalSettings;

    public BillSettingsController(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    @FXML
    public void initialize() {
        setupFormatOptions();
        loadSettings();
        buildSectionsUI();
        updatePreview();
    }

    private void setupFormatOptions() {
        paperWidthCombo.getItems().addAll("2 inch (58 mm)", "3 inch (80 mm)");
        dateFormatCombo.getItems().addAll("standard", "ISO", "short", "long");
        timeFormatCombo.getItems().addAll("12h", "24h");
    }

    private void loadSettings() {
        billSettings = settingsStore.loadBillSettings();
        generalSettings = settingsStore.loadGeneralSettings();

        paperWidthCombo.setValue(formatPaperWidth(billSettings.getPaperWidth()));
        dateFormatCombo.setValue(billSettings.getDateFormat());
        timeFormatCombo.setValue(billSettings.getTimeFormat());

        paperWidthCombo.setOnAction(e -> {
            billSettings.setPaperWidth(parsePaperWidth(paperWidthCombo.getValue()));
            updatePreview();
        });
        dateFormatCombo.setOnAction(e -> {
            billSettings.setDateFormat(dateFormatCombo.getValue());
            updatePreview();
        });
        timeFormatCombo.setOnAction(e -> {
            billSettings.setTimeFormat(timeFormatCombo.getValue());
            updatePreview();
        });

    }

    private void buildSectionsUI() {
        sectionsContainer.getChildren().clear();
        List<BillSection> sections = billSettings.getSections();

        for (int i = 0; i < sections.size(); i++) {
            BillSection section = sections.get(i);
            int index = i;
            VBox sectionBox = createSectionEditor(section, index, i == 0, i == sections.size() - 1);
            sectionsContainer.getChildren().add(sectionBox);
        }
    }

    private VBox createSectionEditor(BillSection section, int index, boolean isFirst, boolean isLast) {
        VBox container = new VBox(10);
        container.getStyleClass().add("card");
        container.setStyle("-fx-padding: 15; -fx-border-color: #e2e8f0; -fx-background-color: #ffffff; -fx-border-radius: 6;");

        HBox header = new HBox(10);
        header.setStyle("-fx-alignment: center-left;");

        CheckBox visibleCheck = new CheckBox(formatSectionName(section.getId()));
        visibleCheck.getStyleClass().add("check-box");
        visibleCheck.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        visibleCheck.setSelected(section.isVisible());
        visibleCheck.setOnAction(e -> {
            section.setVisible(visibleCheck.isSelected());
            updatePreview();
        });

        HBox.setHgrow(visibleCheck, Priority.ALWAYS);

        Button upButton = new Button("▲");
        upButton.getStyleClass().add("icon-button");
        upButton.setTooltip(new Tooltip("Move Up"));
        upButton.setAccessibleText("Move section up");
        upButton.setDisable(isFirst);
        upButton.setOnAction(e -> moveSection(index, -1));

        Button downButton = new Button("▼");
        downButton.getStyleClass().add("icon-button");
        downButton.setTooltip(new Tooltip("Move Down"));
        downButton.setAccessibleText("Move section down");
        downButton.setDisable(isLast);
        downButton.setOnAction(e -> moveSection(index, 1));

        header.getChildren().addAll(visibleCheck, upButton, downButton);
        container.getChildren().add(header);

        if (section.isVisible()) {
            VBox options = createSectionOptions(section);
            container.getChildren().add(options);
        }

        return container;
    }

    private VBox createSectionOptions(BillSection section) {
        VBox options = new VBox(15);
        options.setPadding(new Insets(15, 0, 0, 20));

        boolean hasAlignment = section.getOptions().containsKey("alignment");
        boolean hasFontSize = section.getOptions().containsKey("fontSize");

        if (hasAlignment || hasFontSize) {
            HBox row = new HBox(20);
            row.setStyle("-fx-alignment: center-left;");

            if (hasAlignment) {
                VBox alignBox = new VBox(5);
                alignBox.getStyleClass().add("form-group");
                HBox.setHgrow(alignBox, Priority.ALWAYS);
                Label alignLabel = new Label("Alignment:");
                alignLabel.getStyleClass().add("form-label");
                ComboBox<String> alignCombo = new ComboBox<>();
                alignCombo.getStyleClass().add("combo-box");
                alignCombo.setMaxWidth(Double.MAX_VALUE);
                alignCombo.getItems().addAll("left", "center", "right");
                alignCombo.setValue(section.getOptionAsString("alignment", "left"));
                alignCombo.setOnAction(e -> {
                    section.setOption("alignment", alignCombo.getValue());
                    updatePreview();
                });
                alignBox.getChildren().addAll(alignLabel, alignCombo);
                row.getChildren().add(alignBox);
            }

            if (hasFontSize) {
                VBox sizeBox = new VBox(5);
                sizeBox.getStyleClass().add("form-group");
                HBox.setHgrow(sizeBox, Priority.ALWAYS);
                Label sizeLabel = new Label("Font Size:");
                sizeLabel.getStyleClass().add("form-label");
                ComboBox<String> sizeCombo = new ComboBox<>();
                sizeCombo.getStyleClass().add("combo-box");
                sizeCombo.setMaxWidth(Double.MAX_VALUE);
                sizeCombo.getItems().addAll("small", "medium", "large");
                sizeCombo.setValue(section.getOptionAsString("fontSize", "medium"));
                sizeCombo.setOnAction(e -> {
                    section.setOption("fontSize", sizeCombo.getValue());
                    updatePreview();
                });
                sizeBox.getChildren().addAll(sizeLabel, sizeCombo);
                row.getChildren().add(sizeBox);
            }

            options.getChildren().add(row);
        }

        if (section.getId().equals("storeHeader")) {
            options.getChildren().addAll(
                createTextField("Store Name:", "storeName", section),
                createTextArea("Store Details:", "storeDetails", section),
                createTextField("Phone:", "phone", section),
                createTextField("GSTIN:", "gst", section)
            );

            CheckBox logoCheck = new CheckBox("Show Logo");
            logoCheck.getStyleClass().add("check-box");
            logoCheck.setSelected(section.getOptionAsBoolean("showLogo", false));

            VBox logoPickerBox = buildLogoPickerBox(section);
            logoPickerBox.setVisible(section.getOptionAsBoolean("showLogo", false));
            logoPickerBox.setManaged(section.getOptionAsBoolean("showLogo", false));

            logoCheck.setOnAction(e -> {
                boolean show = logoCheck.isSelected();
                section.setOption("showLogo", show);
                logoPickerBox.setVisible(show);
                logoPickerBox.setManaged(show);
                updatePreview();
            });
            options.getChildren().addAll(logoCheck, logoPickerBox);
        }

        if (section.getId().equals("footer")) {
            options.getChildren().add(createTextArea("Footer Text:", "text", section));
        }

        return options;
    }

    private VBox buildLogoPickerBox(BillSection section) {
        VBox box = new VBox(5);
        box.getStyleClass().add("form-group");
        Label label = new Label("Store Logo:");
        label.getStyleClass().add("form-label");

        HBox controls = new HBox(10);
        controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField logoUrlField = new TextField(section.getOptionAsString("logoUrl", ""));
        logoUrlField.getStyleClass().add("text-field");
        logoUrlField.setEditable(false);
        HBox.setHgrow(logoUrlField, Priority.ALWAYS);

        Button browseButton = new Button("Browse...");
        browseButton.getStyleClass().add("action-button");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Logo Image");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            Window window = box.getScene().getWindow();
            File selectedFile = fileChooser.showOpenDialog(window);
            if (selectedFile != null) {
                try {
                    byte[] fileContent = Files.readAllBytes(selectedFile.toPath());
                    String encodedString = Base64.getEncoder().encodeToString(fileContent);
                    String mimeType = Files.probeContentType(selectedFile.toPath());
                    String dataUri = "data:" + mimeType + ";base64," + encodedString;
                    
                    section.setOption("logoUrl", dataUri);
                    logoUrlField.setText("Image Selected (Base64)");
                    updatePreview();
                } catch (Exception ex) {
                    NotificationService.error("Failed to load image: " + ex.getMessage());
                }
            }
        });

        Button clearButton = new Button("Clear");
        clearButton.getStyleClass().add("secondary-button");
        clearButton.setOnAction(e -> {
            section.setOption("logoUrl", "");
            logoUrlField.setText("");
            updatePreview();
        });

        controls.getChildren().addAll(logoUrlField, browseButton, clearButton);
        box.getChildren().addAll(label, controls);
        
        if (!section.getOptionAsString("logoUrl", "").isEmpty()) {
            logoUrlField.setText("Image Selected (Base64)");
        }

        return box;
    }

    private VBox createTextField(String label, String key, BillSection section) {
        VBox box = new VBox(5);
        box.getStyleClass().add("form-group");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        TextField field = new TextField(section.getOptionAsString(key, ""));
        field.getStyleClass().add("text-field");
        field.setPrefWidth(300);
        field.textProperty().addListener((obs, old, val) -> {
            section.setOption(key, val);
            updatePreview();
        });
        box.getChildren().addAll(lbl, field);
        return box;
    }

    private VBox createTextArea(String label, String key, BillSection section) {
        VBox box = new VBox(5);
        box.getStyleClass().add("form-group");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        TextArea area = new TextArea(section.getOptionAsString(key, ""));
        area.getStyleClass().add("text-area");
        area.setPrefRowCount(4);
        area.setMinHeight(90);
        area.setPrefHeight(100);
        area.setPrefWidth(300);
        area.setWrapText(true);
        area.textProperty().addListener((obs, old, val) -> {
            section.setOption(key, val);
            updatePreview();
        });
        box.getChildren().addAll(lbl, area);
        return box;
    }

    private void moveSection(int index, int direction) {
        List<BillSection> sections = billSettings.getSections();
        BillSection temp = sections.get(index);
        sections.set(index, sections.get(index + direction));
        sections.set(index + direction, temp);
        buildSectionsUI();
        updatePreview();
    }

    private String formatSectionName(String id) {
        return id.replaceAll("([A-Z])", " $1").trim();
    }

    private void updatePreview() {
        Platform.runLater(() -> {
            SaleResponse mockSale = createMockSale();
            String html = BillRenderer.renderBill(mockSale, generalSettings, billSettings);
            WebEngine engine = previewWebView.getEngine();
            engine.loadContent(html);
        });
    }

    private SaleResponse createMockSale() {
        Sale sale = new Sale(
            1L,
            "CH2603260001",
            LocalDateTime.now(),
            BigDecimal.valueOf(315.50),
            BigDecimal.valueOf(315.50),
            BigDecimal.valueOf(10.0),
            BigDecimal.valueOf(15.50),
            "COMPLETED",
            "DELIVERED",
            1L,
            1L,
            "John Doe",
            "555-0199",
            "john@example.com",
            "Jane Smith",
            1L,
            "Cash"
        );

        List<SaleItem> items = new ArrayList<>();
        items.add(new SaleItem(1L, 1L, 1L, "Default", "SKU001", "Product A", 2, BigDecimal.valueOf(50), BigDecimal.valueOf(30), BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.valueOf(5), "[]", BigDecimal.ZERO, 0));
        items.add(new SaleItem(2L, 1L, 2L, "Default", "SKU002", "Product B", 1, BigDecimal.valueOf(150), BigDecimal.valueOf(100), BigDecimal.valueOf(10), BigDecimal.valueOf(15), BigDecimal.valueOf(10), BigDecimal.valueOf(15), "[]", BigDecimal.ZERO, 0));
        items.add(new SaleItem(3L, 1L, 3L, "Default", "SKU003", "Product C - Large", 3, BigDecimal.valueOf(20), BigDecimal.valueOf(10), BigDecimal.valueOf(0), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "[]", BigDecimal.ZERO, 0));

        return new SaleResponse(sale, items, new ArrayList<>());
    }

    @FXML
    private void handleSave() {
        try {
            settingsStore.saveBillSettings(billSettings);
            NotificationService.success("Bill settings saved successfully");
        } catch (Exception e) {
            NotificationService.error("Failed to save settings: " + e.getMessage());
        }
    }

    private String formatPaperWidth(String val) {
        if ("58mm".equals(val)) return "2 inch (58 mm)";
        if ("80mm".equals(val)) return "3 inch (80 mm)";
        return val != null ? val : "3 inch (80 mm)";
    }

    private String parsePaperWidth(String display) {
        if ("2 inch (58 mm)".equals(display)) return "58mm";
        if ("3 inch (80 mm)".equals(display)) return "80mm";
        return display != null ? display : "80mm";
    }
}

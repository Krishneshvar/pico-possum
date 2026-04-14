package com.possum.ui.common.controls;
 
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import com.possum.ui.common.dialogs.DialogStyler;
 
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
 
public class FormDialog extends Dialog<Map<String, Object>> {
    private final VBox container;
    private final GridPane grid;
    private final Map<String, Control> fields = new HashMap<>();
    private int rowIndex = 0;
    private Label subtitleLabel;
 
    public FormDialog(String title) {
        setTitle(title);
 
        container = new VBox(20);
        container.setPadding(new Insets(24));
        container.setPrefWidth(500);
 
        // Header Section
        VBox header = new VBox(8);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: 800;");
        
        subtitleLabel = new Label();
        subtitleLabel.getStyleClass().add("card-subtitle");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setVisible(false);
        subtitleLabel.setManaged(false);
 
        header.getChildren().addAll(titleLabel, subtitleLabel);
        
        Separator sep = new Separator();
        sep.setOpacity(0.4);
 
        grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        
        container.getChildren().addAll(header, sep, grid);
 
        getDialogPane().setContent(container);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(this);
 
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return collectValues();
            }
            return null;
        });
    }
 
    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
        subtitleLabel.setVisible(true);
        subtitleLabel.setManaged(true);
    }
 
    public TextField addTextField(String key, String label, String defaultValue) {
        Label lbl = new Label(label + ":");
        TextField field = new TextField(defaultValue);
        grid.add(lbl, 0, rowIndex);
        grid.add(field, 1, rowIndex);
        fields.put(key, field);
        rowIndex++;
        return field;
    }
 
    public TextArea addTextArea(String key, String label, String defaultValue) {
        Label lbl = new Label(label + ":");
        TextArea area = new TextArea(defaultValue);
        area.setPrefRowCount(3);
        grid.add(lbl, 0, rowIndex);
        grid.add(area, 1, rowIndex);
        fields.put(key, area);
        rowIndex++;
        return area;
    }
 
    public <T> ComboBox<T> addComboBox(String key, String label, T defaultValue) {
        Label lbl = new Label(label + ":");
        ComboBox<T> combo = new ComboBox<>();
        combo.setValue(defaultValue);
        grid.add(lbl, 0, rowIndex);
        grid.add(combo, 1, rowIndex);
        fields.put(key, combo);
        rowIndex++;
        return combo;
    }
 
    public CheckBox addCheckBox(String key, String label, boolean defaultValue) {
        CheckBox check = new CheckBox(label);
        check.setSelected(defaultValue);
        grid.add(check, 1, rowIndex);
        fields.put(key, check);
        rowIndex++;
        return check;
    }
 
    private Map<String, Object> collectValues() {
        Map<String, Object> values = new HashMap<>();
        fields.forEach((key, control) -> {
            if (control instanceof TextField) {
                values.put(key, ((TextField) control).getText());
            } else if (control instanceof TextArea) {
                values.put(key, ((TextArea) control).getText());
            } else if (control instanceof ComboBox) {
                values.put(key, ((ComboBox<?>) control).getValue());
            } else if (control instanceof CheckBox) {
                values.put(key, ((CheckBox) control).isSelected());
            }
        });
        return values;
    }
 
    @SuppressWarnings("unchecked")
    public void setFieldValue(String key, Object value) {
        Control control = fields.get(key);
        if (control instanceof TextField) {
            ((TextField) control).setText(value != null ? value.toString() : "");
        } else if (control instanceof TextArea) {
            ((TextArea) control).setText(value != null ? value.toString() : "");
        } else if (control instanceof ComboBox) {
            ((ComboBox<Object>) control).setValue(value);
        } else if (control instanceof CheckBox) {
            ((CheckBox) control).setSelected((Boolean) value);
        }
    }
 
    public static void show(String title, Consumer<FormDialog> builder, Consumer<Map<String, Object>> onSubmit) {
        FormDialog dialog = new FormDialog(title);
        builder.accept(dialog);
        Optional<Map<String, Object>> result = dialog.showAndWait();
        result.ifPresent(onSubmit);
    }
}

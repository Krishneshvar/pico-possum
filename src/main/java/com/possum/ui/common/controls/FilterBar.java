package com.possum.ui.common.controls;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import java.util.function.Function;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FilterBar extends VBox {
    private final TextField searchField;
    private final Map<String, ComboBox<?>> filters = new HashMap<>();
    private final Map<String, MultiSelectFilter<?>> multiSelectFilters = new HashMap<>();
    private final Map<String, DatePicker> dateFilters = new HashMap<>();
    private final Map<String, TextField> textFilters = new HashMap<>();
    private final Map<String, Object> defaultValues = new HashMap<>();
    private Consumer<Map<String, Object>> onFilterChange;

    private final HBox topRow;
    private final HBox rightActions;
    private final HBox bottomRow;
    private final Button resetButton;
    private boolean isResetting = false;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(220));

    public FilterBar() {
        setSpacing(10);
        setPadding(new Insets(12, 16, 12, 16));
        getStyleClass().add("filter-bar");
        setStyle("-fx-background-color: #FAFBFC; -fx-border-color: #E2E8F0; -fx-border-width: 0 0 1 0;");

        topRow = new HBox(12);
        bottomRow = new HBox(12);
        rightActions = new HBox(8);
        
        topRow.getStyleClass().add("view-toolbar");
        bottomRow.getStyleClass().add("view-toolbar");
        rightActions.getStyleClass().add("view-toolbar");
        
        topRow.setFillHeight(true);
        bottomRow.setFillHeight(true);
        rightActions.setFillHeight(true);
        rightActions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.setAccessibleText("Search records");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(300);
        searchField.setMinWidth(260);
        searchField.setMinHeight(40);
        searchField.setPrefHeight(40);
        searchField.textProperty().addListener((obs, old, val) -> scheduleFilterNotify());
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                notifyFilterChange();
                e.consume();
            }
        });

        resetButton = new Button("Reset");
        FontIcon resetIcon = new FontIcon("bx-reset");
        resetIcon.setIconSize(16);
        resetButton.setGraphic(resetIcon);
        resetButton.getStyleClass().add("action-button");
        resetButton.setAccessibleText("Reset all filters");
        resetButton.setMinHeight(40);
        resetButton.setPrefHeight(40);
        resetButton.setOnAction(e -> reset());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(searchField, resetButton, spacer, rightActions);
        HBox.setHgrow(searchField, Priority.NEVER);

        getChildren().addAll(topRow, bottomRow);

        searchDebounce.setOnFinished(e -> notifyFilterChange());
    }

    public void addTopRightControl(javafx.scene.Node node) {
        rightActions.getChildren().add(node);
    }

    public <T> ComboBox<T> addFilter(String key, String prompt) {
        ComboBox<T> combo = new ComboBox<>();
        combo.setPromptText(prompt);
        combo.setAccessibleText(prompt);
        combo.setMinWidth(176);
        combo.setPrefWidth(188);
        combo.setMinHeight(40);
        combo.setPrefHeight(40);
        combo.valueProperty().addListener((obs, old, val) -> notifyFilterChange());
        
        filters.put(key, combo);
        bottomRow.getChildren().add(combo);
        
        return combo;
    }

    public <T> MultiSelectFilter<T> addMultiSelectFilter(String key, String prompt, java.util.List<T> items, Function<T, String> labelExtractor) {
        return addMultiSelectFilter(key, prompt, items, labelExtractor, true);
    }

    public <T> MultiSelectFilter<T> addMultiSelectFilter(String key, String prompt, java.util.List<T> items, Function<T, String> labelExtractor, boolean searchable) {
        MultiSelectFilter<T> multiSelect = new MultiSelectFilter<>(prompt, labelExtractor, searchable);
        multiSelect.setAccessibleText(prompt);
        multiSelect.setMinWidth(176);
        multiSelect.setPrefWidth(188);
        multiSelect.setMinHeight(40);
        multiSelect.setPrefHeight(40);
        multiSelect.setItems(items);
        multiSelect.getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends T> c) -> notifyFilterChange());

        multiSelectFilters.put(key, multiSelect);
        bottomRow.getChildren().add(multiSelect);

        return multiSelect;
    }

    public DatePicker addDateFilter(String key, String prompt) {
        DatePicker picker = new DatePicker();
        picker.setPromptText(prompt);
        picker.setAccessibleText(prompt);
        picker.setMinWidth(176);
        picker.setPrefWidth(188);
        picker.setMinHeight(40);
        picker.setPrefHeight(40);
        picker.valueProperty().addListener((obs, old, val) -> notifyFilterChange());
        
        dateFilters.put(key, picker);
        bottomRow.getChildren().add(picker);
        return picker;
    }

    public TextField addTextFilter(String key, String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setAccessibleText(prompt);
        field.setMinWidth(152);
        field.setPrefWidth(168);
        field.setMinHeight(40);
        field.setPrefHeight(40);
        field.textProperty().addListener((obs, old, val) -> scheduleFilterNotify());

        textFilters.put(key, field);
        bottomRow.getChildren().add(field);
        return field;
    }

    public void setOnFilterChange(Consumer<Map<String, Object>> handler) {
        this.onFilterChange = handler;
    }

    private void notifyFilterChange() {
        if (onFilterChange != null && !isResetting) {
            Map<String, Object> values = new HashMap<>();
            values.put("search", searchField.getText());
            filters.forEach((key, combo) -> values.put(key, combo.getValue()));
            multiSelectFilters.forEach((key, multiSelect) -> values.put(key, multiSelect.getSelectedItems()));
            dateFilters.forEach((key, picker) -> values.put(key, picker.getValue()));
            textFilters.forEach((key, field) -> values.put(key, field.getText()));
            onFilterChange.accept(values);
        }
    }

    private void scheduleFilterNotify() {
        if (isResetting) {
            return;
        }
        searchDebounce.playFromStart();
    }

    public void reset() {
        isResetting = true;
        try {
            searchField.clear();
            filters.forEach((key, combo) -> {
                Object def = defaultValues.get(key);
                if (def != null) {
                    ((ComboBox) combo).setValue(def);
                } else {
                    combo.setValue(null);
                }
            });
            multiSelectFilters.values().forEach(MultiSelectFilter::clearSelection);
            dateFilters.values().forEach(picker -> picker.setValue(null));
            textFilters.forEach((key, field) -> {
                Object def = defaultValues.get(key);
                field.setText(def != null ? def.toString() : "");
            });
        } finally {
            isResetting = false;
            notifyFilterChange();
        }
    }

    public void setDefaultValue(String key, Object value) {
        defaultValues.put(key, value);
    }

    public String getSearchText() {
        return searchField.getText();
    }

    public String getSearchTerm() {
        return searchField.getText();
    }

    public Object getFilterValue(String key) {
        if (filters.containsKey(key)) return filters.get(key).getValue();
        if (multiSelectFilters.containsKey(key)) return multiSelectFilters.get(key).getSelectedItems();
        if (dateFilters.containsKey(key)) return dateFilters.get(key).getValue();
        if (textFilters.containsKey(key)) return textFilters.get(key).getText();
        if ("search".equals(key)) return searchField.getText();
        return null;
    }

    public void setSearchVisible(boolean visible) {
        searchField.setVisible(visible);
        searchField.setManaged(visible);
    }

    public void setTopRowVisible(boolean visible) {
        topRow.setVisible(visible);
        topRow.setManaged(visible);
    }

    public void setResetInBottomRow(boolean bottom) {
        topRow.getChildren().remove(resetButton);
        bottomRow.getChildren().remove(resetButton);
        if (bottom) {
            bottomRow.getChildren().add(resetButton);
        } else {
            topRow.getChildren().add(1, resetButton);
        }
    }

    public HBox getTopRow() { return topRow; }
    public HBox getBottomRow() { return bottomRow; }
    public HBox getRightActions() { return rightActions; }
}

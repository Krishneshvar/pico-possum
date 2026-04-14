package com.possum.ui.common.controls;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.scene.control.TextField;
import java.util.stream.Collectors;

public class MultiSelectFilter<T> extends MenuButton {

    private final ObservableList<T> selectedItems = FXCollections.observableArrayList();
    private final Function<T, String> labelExtractor;
    private final List<FilterableItem<T>> allItems = new ArrayList<>();
    private final TextField searchField = new TextField();
    private final boolean searchable;
    private final String baseTitle;
    private final VBox itemsBox = new VBox(3);
    private final ScrollPane itemsScroll = new ScrollPane(itemsBox);
    private final VBox popupRoot = new VBox(8);
    private final Button selectAllButton = new Button("Select All");
    private final Button clearButton = new Button("Clear");
    private final int maxVisibleRows = 8;

    public MultiSelectFilter(String title, Function<T, String> labelExtractor) {
        this(title, labelExtractor, true);
    }

    public MultiSelectFilter(String title, Function<T, String> labelExtractor, boolean searchable) {
        super(title);
        this.baseTitle = title;
        this.labelExtractor = labelExtractor;
        this.searchable = searchable;
        getStyleClass().add("multi-select-filter");
        initializePopupContent();
    }

    private void initializePopupContent() {
        popupRoot.getStyleClass().add("multi-select-popup");
        itemsBox.getStyleClass().add("multi-select-container");

        if (searchable) {
            searchField.setPromptText("Type to filter...");
            searchField.getStyleClass().add("multi-select-search");
            searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshVisibleItems(newVal));
            popupRoot.getChildren().add(searchField);
        }

        HBox quickActions = new HBox(8);
        quickActions.getStyleClass().add("multi-select-actions");
        selectAllButton.getStyleClass().add("action-btn");
        clearButton.getStyleClass().add("action-btn");
        selectAllButton.setOnAction(e -> selectAllVisible());
        clearButton.setOnAction(e -> clearSelection());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        quickActions.getChildren().addAll(selectAllButton, spacer, clearButton);
        popupRoot.getChildren().add(quickActions);

        itemsScroll.setFitToWidth(true);
        itemsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        itemsScroll.getStyleClass().add("multi-select-scroll");
        popupRoot.getChildren().add(itemsScroll);

        CustomMenuItem popupItem = new CustomMenuItem(popupRoot);
        popupItem.setHideOnClick(false);
        getItems().setAll(popupItem);

        selectedItems.addListener((javafx.collections.ListChangeListener<T>) c -> updateButtonText());
        showingProperty().addListener((obs, oldVal, showing) -> {
            if (!showing) {
                return;
            }
            javafx.application.Platform.runLater(() -> {
                adjustPopupSizing();
                if (searchable) {
                    searchField.requestFocus();
                    searchField.selectAll();
                }
            });
        });
    }

    public void setItems(List<T> items) {
        List<T> safeItems = items == null ? List.of() : items;
        searchField.clear();
        selectedItems.clear();
        allItems.clear();
        itemsBox.getChildren().clear();

        double maxTextWidth = 0;
        for (T item : safeItems) {
            String label = labelExtractor.apply(item);
            CheckBox checkBox = new CheckBox(label);
            checkBox.getStyleClass().add("multi-select-option");

            checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    if (!selectedItems.contains(item)) selectedItems.add(item);
                } else {
                    selectedItems.remove(item);
                }
            });
            
            checkBox.setMaxWidth(Double.MAX_VALUE);
            allItems.add(new FilterableItem<>(item, checkBox, label));
            itemsBox.getChildren().add(checkBox);
            maxTextWidth = Math.max(maxTextWidth, computeLabelWidth(label));
        }

        adjustPopupSizing(maxTextWidth);
        refreshVisibleItems("");
        updateButtonText();
    }

    private void refreshVisibleItems(String query) {
        String lowerQuery = query == null ? "" : query.toLowerCase().trim();
        itemsBox.getChildren().clear();

        List<FilterableItem<T>> visibleItems = allItems.stream()
            .filter(item -> lowerQuery.isEmpty() || item.label().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());

        for (FilterableItem<T> item : visibleItems) {
            itemsBox.getChildren().add(item.checkBox());
        }

        double rowHeight = 32;
        double viewportHeight = Math.min(maxVisibleRows, Math.max(1, visibleItems.size())) * rowHeight + 6;
        itemsScroll.setPrefViewportHeight(viewportHeight);
        itemsScroll.setMinViewportHeight(viewportHeight);
        itemsScroll.setVvalue(0);
    }

    private void selectAllVisible() {
        for (javafx.scene.Node node : itemsBox.getChildren()) {
            if (node instanceof CheckBox checkBox && !checkBox.isSelected()) {
                checkBox.setSelected(true);
            }
        }
    }

    public ObservableList<T> getSelectedItems() {
        return selectedItems;
    }

    public void clearSelection() {
        for (FilterableItem<T> item : allItems) {
            item.checkBox().setSelected(false);
        }
        selectedItems.clear();
        updateButtonText();
    }

    public void selectItems(List<T> items) {
        if (items == null) return;
        for (FilterableItem<T> item : allItems) {
            if (items.contains(item.item())) {
                item.checkBox().setSelected(true);
            }
        }
    }

    private void updateButtonText() {
        if (selectedItems.isEmpty()) {
            setText(baseTitle);
            return;
        }

        if (selectedItems.size() <= 2) {
            String labels = selectedItems.stream()
                .map(labelExtractor)
                .collect(Collectors.joining(", "));
            setText(labels);
            return;
        }

        setText(baseTitle + " (" + selectedItems.size() + ")");
    }

    private double computeLabelWidth(String text) {
        Text helper = new Text(text == null ? "" : text);
        helper.setStyle("-fx-font-size: 14px;");
        return Math.ceil(helper.getLayoutBounds().getWidth());
    }

    private void adjustPopupSizing() {
        double maxText = allItems.stream().mapToDouble(item -> computeLabelWidth(item.label())).max().orElse(140);
        adjustPopupSizing(maxText);
    }

    private void adjustPopupSizing(double maxTextWidth) {
        double buttonWidth = Math.max(getWidth(), getPrefWidth());
        double targetWidth = Math.max(buttonWidth, Math.min(420, Math.max(230, maxTextWidth + 92)));
        popupRoot.setPrefWidth(targetWidth);
        popupRoot.setMinWidth(targetWidth);
        itemsScroll.setPrefViewportWidth(targetWidth - 16);
        if (searchable) {
            searchField.setPrefWidth(targetWidth - 16);
        }
    }

    private record FilterableItem<T>(T item, CheckBox checkBox, String label) {}
}

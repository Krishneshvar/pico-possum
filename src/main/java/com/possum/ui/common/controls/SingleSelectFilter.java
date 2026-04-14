package com.possum.ui.common.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SingleSelectFilter<T> extends MenuButton {

    private final ObjectProperty<T> selectedItem = new SimpleObjectProperty<>();
    private final Function<T, String> labelExtractor;
    private final List<FilterableItem<T>> allItems = new ArrayList<>();
    private final TextField searchField = new TextField();
    private final boolean searchable;
    private String baseTitle;
    private final VBox itemsBox = new VBox(2);
    private final ScrollPane itemsScroll = new ScrollPane(itemsBox);
    private final VBox popupRoot = new VBox(8);
    private final Button clearButton = new Button("Clear Selection");
    private final int maxVisibleRows = 8;

    public SingleSelectFilter() {
        this("", item -> item == null ? "" : item.toString(), true);
    }

    public SingleSelectFilter(String title, Function<T, String> labelExtractor) {
        this(title, labelExtractor, true);
    }

    public void setTitle(String title) {
        this.baseTitle = title;
        updateButtonText();
    }

    public String getTitle() {
        return baseTitle;
    }

    public SingleSelectFilter(String title, Function<T, String> labelExtractor, boolean searchable) {
        super(title);
        this.baseTitle = title;
        this.labelExtractor = labelExtractor;
        this.searchable = searchable;
        getStyleClass().add("single-select-filter");
        initializePopupContent();
    }

    private void initializePopupContent() {
        popupRoot.getStyleClass().add("multi-select-popup"); // Reuse style for consistency
        itemsBox.getStyleClass().add("multi-select-container");

        if (searchable) {
            searchField.setPromptText("Search...");
            searchField.getStyleClass().add("multi-select-search");
            searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshVisibleItems(newVal));
            popupRoot.getChildren().add(searchField);
        }

        HBox quickActions = new HBox(8);
        quickActions.getStyleClass().add("multi-select-actions");
        clearButton.getStyleClass().add("action-btn");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clearButton, Priority.ALWAYS);
        clearButton.setOnAction(e -> {
            setSelectedItem(null);
            hide();
        });
        quickActions.getChildren().add(clearButton);
        popupRoot.getChildren().add(quickActions);

        itemsScroll.setFitToWidth(true);
        itemsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        itemsScroll.getStyleClass().add("multi-select-scroll");
        popupRoot.getChildren().add(itemsScroll);

        CustomMenuItem popupItem = new CustomMenuItem(popupRoot);
        popupItem.setHideOnClick(false);
        getItems().setAll(popupItem);

        selectedItem.addListener((obs, oldVal, newVal) -> updateButtonText());
        showingProperty().addListener((obs, oldVal, showing) -> {
            if (!showing) return;
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
        setSelectedItem(null);
        allItems.clear();
        itemsBox.getChildren().clear();

        double maxTextWidth = 0;
        for (T item : safeItems) {
            String label = labelExtractor.apply(item);
            Button itemBtn = new Button(label);
            itemBtn.getStyleClass().add("multi-select-option-btn");
            itemBtn.setMaxWidth(Double.MAX_VALUE);
            itemBtn.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            
            itemBtn.setOnAction(e -> {
                setSelectedItem(item);
                hide();
            });

            allItems.add(new FilterableItem<>(item, itemBtn, label));
            itemsBox.getChildren().add(itemBtn);
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
            itemsBox.getChildren().add(item.button());
        }

        double rowHeight = 36;
        double viewportHeight = Math.min(maxVisibleRows, Math.max(1, visibleItems.size())) * rowHeight + 6;
        itemsScroll.setPrefViewportHeight(viewportHeight);
        itemsScroll.setMinViewportHeight(viewportHeight);
        itemsScroll.setVvalue(0);
    }

    public ObjectProperty<T> selectedItemProperty() {
        return selectedItem;
    }

    public T getSelectedItem() {
        return selectedItem.get();
    }

    public void setSelectedItem(T item) {
        selectedItem.set(item);
        // Highlight logic could go here
    }

    private void updateButtonText() {
        if (selectedItem.get() == null) {
            setText(baseTitle);
        } else {
            setText(labelExtractor.apply(selectedItem.get()));
        }
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
        double targetWidth = Math.max(buttonWidth, Math.min(420, Math.max(230, maxTextWidth + 40)));
        popupRoot.setPrefWidth(targetWidth);
        popupRoot.setMinWidth(targetWidth);
        itemsScroll.setPrefViewportWidth(targetWidth - 16);
        if (searchable) {
            searchField.setPrefWidth(targetWidth - 16);
        }
    }

    private record FilterableItem<T>(T item, Button button, String label) {}
}

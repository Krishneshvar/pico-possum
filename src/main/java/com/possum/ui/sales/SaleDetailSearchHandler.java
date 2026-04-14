package com.possum.ui.sales;

import com.possum.domain.model.Variant;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import com.possum.shared.util.CurrencyUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class SaleDetailSearchHandler {

    private final TextField itemSearchField;
    private final ProductSearchIndex searchIndex;
    private final Consumer<Variant> onVariantSelected;

    private final Popup searchPopup = new Popup();
    private final ListView<Variant> searchResultsView = new ListView<>(FXCollections.observableArrayList());

    public SaleDetailSearchHandler(TextField itemSearchField, 
                                   ProductSearchIndex searchIndex, 
                                   Consumer<Variant> onVariantSelected) {
        this.itemSearchField = itemSearchField;
        this.searchIndex = searchIndex;
        this.onVariantSelected = onVariantSelected;
    }

    public void setup() {
        searchResultsView.getStyleClass().add("search-results-list");
        searchPopup.getContent().add(searchResultsView);
        searchPopup.setAutoHide(true);
        
        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Variant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else {
                    VBox b = new VBox(2);
                    b.setPrefHeight(45);
                    Label n = new Label(item.productName() + (item.name().equals("Standard") ? "" : " - " + item.name()));
                    n.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                    Label d = new Label(item.sku() + " • " + CurrencyUtil.format(item.price()));
                    d.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                    b.getChildren().addAll(n, d);
                    setGraphic(b);
                }
            }
        });

        searchResultsView.setOnMouseClicked(e -> {
            Variant selected = searchResultsView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectVariant(selected);
            }
        });

        searchResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Variant selected = searchResultsView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectVariant(selected);
                }
            }
        });

        itemSearchField.textProperty().addListener((obs, oldV, newVal) -> {
            String query = newVal != null ? newVal.trim() : "";
            if (query.isEmpty()) {
                searchPopup.hide();
                return;
            }

            // SKU Quick Add
            Optional<Variant> bySku = searchIndex.findBySku(query);
            if (bySku.isPresent()) {
                selectVariant(bySku.get());
                return;
            }

            // Normal Search Popup
            List<Variant> results = searchIndex.searchByName(query);
            if (!results.isEmpty()) {
                searchResultsView.getItems().setAll(results);
                searchResultsView.setPrefHeight(Math.min(results.size() * 52 + 10, 400));
                searchResultsView.setPrefWidth(Math.max(itemSearchField.getWidth(), 300));
                
                Point2D p = itemSearchField.localToScreen(0, itemSearchField.getHeight() + 5);
                if (p != null) {
                    searchPopup.show(itemSearchField, p.getX(), p.getY());
                }
            } else {
                searchPopup.hide();
            }
        });

        itemSearchField.focusedProperty().addListener((obs, oldF, newF) -> {
            if (!newF) {
                Platform.runLater(() -> {
                    if (!searchResultsView.isFocused()) searchPopup.hide();
                });
            }
        });

        itemSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && searchPopup.isShowing()) {
                searchResultsView.requestFocus();
                searchResultsView.getSelectionModel().select(0);
            }
        });
    }

    private void selectVariant(Variant variant) {
        onVariantSelected.accept(variant);
        searchPopup.hide();
        itemSearchField.clear();
    }
}

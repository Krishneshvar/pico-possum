package com.picopossum.ui.sales;

import com.picopossum.domain.model.Product;
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

import com.picopossum.shared.util.CurrencyUtil;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class SaleDetailSearchHandler {

    private final TextField itemSearchField;
    private final ProductSearchIndex searchIndex;
    private final Consumer<Product> onProductSelected;

    private final Popup searchPopup = new Popup();
    private final ListView<Product> searchResultsView = new ListView<>(FXCollections.observableArrayList());

    public SaleDetailSearchHandler(TextField itemSearchField, 
                                   ProductSearchIndex searchIndex, 
                                   Consumer<Product> onProductSelected) {
        this.itemSearchField = itemSearchField;
        this.searchIndex = searchIndex;
        this.onProductSelected = onProductSelected;
    }

    public void setup() {
        String css = getClass().getResource("/styles/pos.css").toExternalForm();
        if (!searchResultsView.getStylesheets().contains(css)) {
            searchResultsView.getStylesheets().add(css);
        }
        searchResultsView.getStyleClass().add("search-results-list");
        searchPopup.getContent().add(searchResultsView);
        searchPopup.setAutoHide(true);
        
        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); }
                else {
                    VBox b = new VBox(2);
                    b.getStyleClass().add("search-item-box");
                    
                    Label n = new Label(item.name());
                    n.getStyleClass().add("search-item-name");
                    
                    String stockStr = item.stock() != null ? String.valueOf(item.stock()) : "∞";
                    Label d = new Label(item.sku() + " • " + CurrencyUtil.format(item.mrp()) + " • Stock: " + stockStr);
                    d.getStyleClass().add("search-item-details");
                    
                    b.getChildren().addAll(n, d);
                    setGraphic(b);
                }
            }
        });

        searchResultsView.setOnMouseClicked(e -> {
            Product selected = searchResultsView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectProduct(selected);
            }
        });

        searchResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Product selected = searchResultsView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectProduct(selected);
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
            Optional<Product> bySku = searchIndex.findBySku(query);
            if (bySku.isPresent()) {
                selectProduct(bySku.get());
                return;
            }

            // Normal Search Popup
            List<Product> results = searchIndex.searchByName(query);
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

    private void selectProduct(Product product) {
        onProductSelected.accept(product);
        searchPopup.hide();
        itemSearchField.clear();
    }
}

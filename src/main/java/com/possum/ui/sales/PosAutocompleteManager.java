package com.possum.ui.sales;

import com.possum.domain.model.CartItem;
import com.possum.domain.model.Category;
import com.possum.domain.model.Variant;
import com.possum.ui.common.controls.NotificationService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import com.possum.shared.util.CurrencyUtil;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the three Popup autocomplete lists in the POS screen:
 * – main product search
 * – quick-add product lookup
 * – quick-add category lookup
 *
 * Keeps all popup layout/event wiring out of PosController.
 */
public class PosAutocompleteManager {

    public interface Callbacks {
        void onProductSelected(Variant v);
        void onProductSelectedForQuickAdd(Variant v);
        void onCategorySelectedForQuickAdd(Category c);
        ProductSearchIndex getSearchIndex();
        com.possum.application.categories.CategoryService getCategoryService();
    }

    private final Callbacks cb;

    // Main product search popup
    private final Popup searchPopup = new Popup();
    private final ListView<Variant> searchResultsView = new ListView<>();

    // Quick-add product popup
    private final Popup quickProductPopup = new Popup();
    private final ListView<Variant> quickProductResultsView = new ListView<>();

    // Quick-add category popup
    private final Popup quickCategoryPopup = new Popup();
    private final ListView<Category> quickCategoryResultsView = new ListView<>();

    public PosAutocompleteManager(Callbacks cb) {
        this.cb = cb;
    }

    // ── Accessors used by PosController ──────────────────────────────────────

    public Popup getSearchPopup() { return searchPopup; }
    public ListView<Variant> getSearchResultsView() { return searchResultsView; }
    public Popup getQuickProductPopup() { return quickProductPopup; }
    public Popup getQuickCategoryPopup() { return quickCategoryPopup; }

    // ── Setup ─────────────────────────────────────────────────────────────────

    public void setupSearchAutocomplete(javafx.scene.control.TextField searchField) {
        applyStyles(searchResultsView);
        searchPopup.getContent().add(searchResultsView);
        searchPopup.setAutoHide(true);

        searchResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Variant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                VBox box = new VBox(2);
                box.getStyleClass().add("search-item-box");
                Label name = new Label(item.productName() + (item.name().equals("Default") ? "" : " - " + item.name()));
                name.getStyleClass().add("search-item-name");
                Label detail = new Label(item.sku() + " • " + CurrencyUtil.format(item.price()) + " • Stock: " + (item.stock() != null ? item.stock() : "∞"));
                detail.getStyleClass().add("search-item-details");
                box.getChildren().addAll(name, detail);
                setGraphic(box);
            }
        });

        searchResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Variant v = searchResultsView.getSelectionModel().getSelectedItem();
                if (v != null) { cb.onProductSelected(v); searchField.clear(); searchPopup.hide(); }
            } else if (e.getCode() == KeyCode.UP && searchResultsView.getSelectionModel().getSelectedIndex() == 0) {
                searchField.requestFocus();
            }
        });

        searchResultsView.setOnMouseClicked(e -> {
            Variant v = searchResultsView.getSelectionModel().getSelectedItem();
            if (v != null) Platform.runLater(() -> { cb.onProductSelected(v); searchField.clear(); searchPopup.hide(); });
        });

        searchField.textProperty().addListener((o, ol, q) -> showSearchPopup(searchField, q != null ? q.trim() : ""));
        searchField.focusedProperty().addListener((o, ol, f) -> {
            if (!f) Platform.runLater(() -> { if (!searchResultsView.isFocused()) searchPopup.hide(); });
        });
    }

    public void showSearchPopup(javafx.scene.control.TextField anchor, String query) {
        List<Variant> res = query.isEmpty() ? cb.getSearchIndex().searchByName("") : cb.getSearchIndex().searchByName(query);
        if (res.isEmpty() && query.isEmpty()) res = cb.getSearchIndex().searchByName(" ");
        if (!res.isEmpty()) {
            searchResultsView.getItems().setAll(res);
            searchResultsView.setPrefHeight(Math.min(res.size() * 52 + 10, 400));
            searchResultsView.setPrefWidth(Math.max(anchor.getWidth(), 300));
            javafx.geometry.Point2D p = anchor.localToScreen(0, anchor.getHeight() + 5);
            if (p != null) {
                if (p.getY() + searchResultsView.getPrefHeight() > anchor.getScene().getWindow().getHeight())
                    searchPopup.show(anchor, p.getX(), p.getY() - anchor.getHeight() - searchResultsView.getPrefHeight() - 10);
                else searchPopup.show(anchor, p.getX(), p.getY());
            }
        } else searchPopup.hide();
    }

    public void setupQuickAddAutocomplete(javafx.scene.control.TextField productField,
                                           javafx.scene.control.TextField variantField) {
        applyStyles(quickProductResultsView);
        quickProductPopup.getContent().add(quickProductResultsView);
        quickProductPopup.setAutoHide(true);

        quickProductResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Variant item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                VBox box = new VBox(2);
                box.getStyleClass().add("search-item-box");
                Label name = new Label(item.productName()); name.getStyleClass().add("search-item-name");
                Label detail = new Label(item.categoryName() != null ? item.categoryName() : "No Category");
                detail.getStyleClass().add("search-item-details");
                box.getChildren().addAll(name, detail); setGraphic(box);
            }
        });

        productField.textProperty().addListener((o, ol, q) -> showQuickProductPopup(productField, q != null ? q.trim() : ""));
        quickProductResultsView.setOnMouseClicked(e -> {
            Variant v = quickProductResultsView.getSelectionModel().getSelectedItem();
            if (v != null) { cb.onProductSelectedForQuickAdd(v); quickProductPopup.hide(); variantField.requestFocus(); }
        });
        quickProductResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Variant v = quickProductResultsView.getSelectionModel().getSelectedItem();
                if (v != null) { cb.onProductSelectedForQuickAdd(v); quickProductPopup.hide(); variantField.requestFocus(); }
            }
        });
    }

    private void showQuickProductPopup(javafx.scene.control.TextField anchor, String query) {
        if (query.isEmpty()) { quickProductPopup.hide(); return; }
        List<Variant> res = cb.getSearchIndex().searchByName(query).stream()
                .filter(distinctByKey(Variant::productId)).limit(10).toList();
        if (!res.isEmpty()) {
            quickProductResultsView.getItems().setAll(res);
            quickProductResultsView.setPrefHeight(Math.min(res.size() * 52 + 10, 300));
            quickProductResultsView.setPrefWidth(Math.max(anchor.getWidth(), 300));
            javafx.geometry.Point2D p = anchor.localToScreen(0, anchor.getHeight() + 2);
            if (p != null) quickProductPopup.show(anchor, p.getX(), p.getY());
        } else quickProductPopup.hide();
    }

    public void setupCategoryAutocomplete(javafx.scene.control.TextField categoryField) {
        applyStyles(quickCategoryResultsView);
        quickCategoryPopup.getContent().add(quickCategoryResultsView);
        quickCategoryPopup.setAutoHide(true);

        quickCategoryResultsView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setText(null); setGraphic(null); return; }
                VBox box = new VBox(2); box.getStyleClass().add("search-item-box");
                Label name = new Label(it.name()); name.getStyleClass().add("search-item-name");
                box.getChildren().add(name); setGraphic(box); setText(null);
            }
        });

        categoryField.textProperty().addListener((o, ol, q) -> showCategoryPopup(categoryField, q != null ? q.trim() : ""));
        quickCategoryResultsView.setOnMouseClicked(e -> {
            Category c = quickCategoryResultsView.getSelectionModel().getSelectedItem();
            if (c != null) { cb.onCategorySelectedForQuickAdd(c); quickCategoryPopup.hide(); }
        });
        quickCategoryResultsView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Category c = quickCategoryResultsView.getSelectionModel().getSelectedItem();
                if (c != null) { cb.onCategorySelectedForQuickAdd(c); quickCategoryPopup.hide(); }
            }
        });
    }

    private void showCategoryPopup(javafx.scene.control.TextField anchor, String query) {
        if (query.isEmpty()) { quickCategoryPopup.hide(); return; }
        List<Category> res = cb.getCategoryService().getAllCategories().stream()
                .filter(c -> c.name().toLowerCase().contains(query.toLowerCase())).limit(10).toList();
        if (!res.isEmpty()) {
            quickCategoryResultsView.getItems().setAll(res);
            quickCategoryResultsView.setPrefHeight(Math.min(res.size() * 36 + 10, 250));
            quickCategoryResultsView.setPrefWidth(Math.max(anchor.getWidth(), 250));
            javafx.geometry.Point2D p = anchor.localToScreen(0, anchor.getHeight() + 2);
            if (p != null) quickCategoryPopup.show(anchor, p.getX(), p.getY());
        } else quickCategoryPopup.hide();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void applyStyles(ListView<?> lv) {
        String css = Objects.requireNonNull(getClass().getResource("/styles/pos.css")).toExternalForm();
        if (!lv.getStylesheets().contains(css)) lv.getStylesheets().add(css);
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> key) {
        java.util.Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(key.apply(t));
    }
}

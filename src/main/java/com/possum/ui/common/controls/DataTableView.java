package com.possum.ui.common.controls;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Cursor;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.List;

/**
 * A reusable, standardized table component for the Possum UI.
 * Wraps a standard TableView with loading overlays and empty states.
 */
public class DataTableView<T> extends StackPane {
    
    @FXML private TableView<T> tableView;
    @FXML private VBox emptyState;
    @FXML private FontIcon emptyIcon;
    @FXML private Label emptyMessage;
    @FXML private Label emptySubtitle;
    @FXML private StackPane loadingOverlay;
    
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    public DataTableView() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/common/controls/data-table-view.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DataTableView FXML", e);
        }
    }

    @FXML
    public void initialize() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setAccessibleText("Data table");
        
        // Remove from StackPane children list so it doesn't cause JavaFX layout index crashes when TableViewSkin claims it
        getChildren().remove(emptyState);
        
        // Use TableView's built-in placeholder support
        tableView.setPlaceholder(emptyState);
        
        loading.addListener((obs, old, isLoading) -> {
            loadingOverlay.setVisible(isLoading);
            loadingOverlay.setManaged(isLoading);
            
            // Toggle visibility of table and placeholder during loading
            if (isLoading) {
                tableView.setPlaceholder(new Label("")); // Temporary empty placeholder
            } else {
                tableView.setPlaceholder(emptyState);
            }
        });
        
        // Hide overlay initially
        loadingOverlay.setVisible(false);
        loadingOverlay.setManaged(false);
    }

    @SuppressWarnings("unchecked")
    public TableColumn<T, ?> addColumn(String title, Callback<TableColumn.CellDataFeatures<T, ?>, ?> valueFactory) {
        TableColumn<T, ?> column = new TableColumn<>(title);
        column.setCellValueFactory((Callback) valueFactory);
        column.setSortable(true);
        tableView.getColumns().add(column);
        return column;
    }

    public void addActionColumn(String title, Consumer<T> onAction) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setMinWidth(132);
        column.setPrefWidth(152);
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(col -> new TableCell<T, T>() {
            private final Button button = new Button(title);
            {
                button.setCursor(Cursor.HAND);
                button.getStyleClass().add("action-btn");
                button.getStyleClass().add("table-action-btn");
                button.setAccessibleText(title + " action");
                button.setMnemonicParsing(false);
                button.setOnAction(e -> {
                    T item = getItem();
                    if (item != null) {
                        onAction.accept(item);
                    }
                });
            }
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(button);
                }
            }
        });
        tableView.getColumns().add(column);
    }

    public void addMenuActionColumn(String title, Function<T, List<MenuItem>> menuBuilder) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setSortable(false);
        column.setMinWidth(132);
        column.setPrefWidth(152);
        column.setCellValueFactory(features -> new ReadOnlyObjectWrapper<>(features.getValue()));
        column.setCellFactory(col -> new TableCell<T, T>() {
            private final Button menuButton = new Button(title + " \u25BE");
            private final ContextMenu actionsMenu = new ContextMenu();
            {
                menuButton.setCursor(Cursor.HAND);
                menuButton.getStyleClass().add("action-btn");
                menuButton.getStyleClass().add("table-action-btn");
                menuButton.getStyleClass().add("table-action-menu-trigger");
                menuButton.setAccessibleText(title + " actions menu");
                menuButton.setMnemonicParsing(false);
                menuButton.setOnAction(e -> {
                    T item = getItem();
                    if (item != null) {
                        actionsMenu.getItems().setAll(menuBuilder.apply(item));
                        if (!actionsMenu.getItems().isEmpty()) {
                            actionsMenu.show(menuButton, Side.BOTTOM, 0, 4);
                        }
                    }
                });
            }
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    actionsMenu.hide();
                    setGraphic(null);
                } else {
                    setGraphic(menuButton);
                }
            }
        });
        tableView.getColumns().add(column);
    }

    public void setItems(ObservableList<T> items) {
        tableView.setItems(items);
    }

    public ObservableList<T> getItems() {
        return tableView.getItems();
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public TableView<T> getTableView() {
        return tableView;
    }

    public void setEmptyMessage(String message) {
        emptyMessage.setText(message);
        checkPlaceholderVisibility();
    }

    public String getEmptyMessage() {
        return emptyMessage.getText();
    }

    public void setEmptySubtitle(String subtitle) {
        emptySubtitle.setText(subtitle);
    }

    public void setEmptyIcon(String icon) {
        emptyIcon.setText(icon);
    }

    public void setEmptyIconStyle(String style) {
        emptyIcon.setStyle(style);
    }
    
    private void checkPlaceholderVisibility() {
        // Force refresh placeholder if necessary
        if (!isLoading() && (getItems() == null || getItems().isEmpty())) {
            tableView.setPlaceholder(emptyState);
        }
    }
}

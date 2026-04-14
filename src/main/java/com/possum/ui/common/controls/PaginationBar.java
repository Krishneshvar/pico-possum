package com.possum.ui.common.controls;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.function.BiConsumer;

public class PaginationBar extends HBox {
    private final Pagination pagination;
    private final ComboBox<Integer> pageSizeCombo;
    private final Label infoLabel;
    private BiConsumer<Integer, Integer> onPageChange;
    private int totalItems = 0;

    public PaginationBar() {
        setSpacing(12);
        setPadding(new Insets(12, 16, 12, 16));
        setAlignment(Pos.CENTER);
        getStyleClass().add("pagination-bar");
        setStyle("-fx-background-color: #FAFBFC; -fx-border-color: #E2E8F0; -fx-border-width: 1 0 0 0;");

        pagination = new Pagination();
        pagination.setMaxPageIndicatorCount(5);
        pagination.currentPageIndexProperty().addListener((obs, old, newPage) -> {
            updateInfo();
            notifyPageChange();
        });

        pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getItems().addAll(10, 25, 50, 100);
        pageSizeCombo.setValue(25);
        pageSizeCombo.setMinHeight(40);
        pageSizeCombo.setPrefHeight(40);
        pageSizeCombo.valueProperty().addListener((obs, old, newSize) -> {
            updatePageCount();
            notifyPageChange();
        });

        infoLabel = new Label();
        infoLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        updateInfo();

        HBox leftBox = new HBox(10, new Label("Items per page:"), pageSizeCombo);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #475569;");

        HBox rightBox = new HBox(10, infoLabel);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(leftBox, pagination, rightBox);
        HBox.setHgrow(pagination, Priority.ALWAYS);
    }

    public void setTotalItems(int total) {
        this.totalItems = total;
        updatePageCount();
        updateInfo();
    }

    public void setOnPageChange(BiConsumer<Integer, Integer> handler) {
        this.onPageChange = handler;
    }

    private void updatePageCount() {
        int pageSize = pageSizeCombo.getValue();
        int pageCount = (int) Math.ceil((double) totalItems / pageSize);
        pagination.setPageCount(Math.max(1, pageCount));
    }

    private void updateInfo() {
        int page = pagination.getCurrentPageIndex();
        int pageSize = pageSizeCombo.getValue();
        int start = page * pageSize + 1;
        int end = Math.min((page + 1) * pageSize, totalItems);
        infoLabel.setText(String.format("Showing %d-%d of %d", start, end, totalItems));
    }

    private void notifyPageChange() {
        if (onPageChange != null) {
            onPageChange.accept(pagination.getCurrentPageIndex(), pageSizeCombo.getValue());
        }
    }

    public int getCurrentPage() {
        return pagination.getCurrentPageIndex();
    }

    public int getPageSize() {
        return pageSizeCombo.getValue();
    }

    public void reset() {
        if (pagination.getCurrentPageIndex() == 0) {
            notifyPageChange();
        } else {
            pagination.setCurrentPageIndex(0);
        }
    }
}

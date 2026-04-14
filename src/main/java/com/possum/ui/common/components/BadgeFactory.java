package com.possum.ui.common.components;

import com.possum.shared.util.TextFormatter;
import com.possum.ui.common.styles.StatusStyleMapper;
import javafx.scene.control.Label;

/**
 * Factory for creating styled badge labels.
 * Provides consistent badge appearance across the application.
 */
public final class BadgeFactory {

    private BadgeFactory() {
        // Utility class
    }

    /**
     * Badge style types.
     */
    public enum BadgeStyle {
        SUCCESS("badge-success"),
        WARNING("badge-warning"),
        ERROR("badge-error"),
        INFO("badge-info"),
        NEUTRAL("badge-neutral");

        private final String styleClass;

        BadgeStyle(String styleClass) {
            this.styleClass = styleClass;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    /**
     * Create a status badge with automatic style based on status value.
     */
    public static Label createStatusBadge(String status) {
        Label badge = new Label(TextFormatter.formatStatus(status));
        badge.getStyleClass().addAll("badge", "badge-status");
        StatusStyleMapper.applyStatusStyle(badge, status);
        return badge;
    }

    /**
     * Create a product status badge.
     */
    public static Label createProductStatusBadge(String status) {
        Label badge = new Label(TextFormatter.formatStatus(status));
        badge.getStyleClass().addAll("badge", "badge-status");
        StatusStyleMapper.applyProductStatusStyle(badge, status);
        return badge;
    }

    /**
     * Create a badge with specific style.
     */
    public static Label createBadge(String text, BadgeStyle style) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", style.getStyleClass());
        return badge;
    }

    /**
     * Create a badge with custom style class.
     */
    public static Label createBadge(String text, String customStyleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", "badge-status", customStyleClass);
        return badge;
    }

    /**
     * Create a simple badge with default styling.
     */
    public static Label createBadge(String text) {
        return createBadge(text, BadgeStyle.NEUTRAL);
    }

    /**
     * Create a success badge.
     */
    public static Label createSuccessBadge(String text) {
        return createBadge(text, BadgeStyle.SUCCESS);
    }

    /**
     * Create a warning badge.
     */
    public static Label createWarningBadge(String text) {
        return createBadge(text, BadgeStyle.WARNING);
    }

    /**
     * Create an error badge.
     */
    public static Label createErrorBadge(String text) {
        return createBadge(text, BadgeStyle.ERROR);
    }

    /**
     * Create an info badge.
     */
    public static Label createInfoBadge(String text) {
        return createBadge(text, BadgeStyle.INFO);
    }

    /**
     * Create a count badge (for notifications, etc.).
     */
    public static Label createCountBadge(int count) {
        Label badge = new Label(String.valueOf(count));
        badge.getStyleClass().addAll("badge", "badge-count");
        return badge;
    }

    /**
     * Create a user status badge (Active/Inactive).
     */
    public static Label createUserStatusBadge(boolean isActive) {
        String text = isActive ? "Active" : "Inactive";
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", "badge-status");
        badge.getStyleClass().add(StatusStyleMapper.getUserStatusClass(isActive));
        return badge;
    }

    /**
     * Create a purchase order status badge.
     */
    public static Label createPurchaseStatusBadge(String status) {
        Label badge = new Label(status.toUpperCase());
        badge.getStyleClass().addAll("badge", "badge-status");
        
        switch (status.toLowerCase()) {
            case "pending" -> badge.getStyleClass().add("badge-warning");
            case "received" -> badge.getStyleClass().add("badge-success");
            case "cancelled" -> badge.getStyleClass().add("badge-error");
            default -> badge.getStyleClass().add("badge-neutral");
        }
        
        return badge;
    }

    /**
     * Create a sale status badge.
     */
    public static Label createSaleStatusBadge(String status) {
        Label badge = new Label(status.replace("_", " ").toUpperCase());
        badge.getStyleClass().addAll("badge", "badge-status");
        
        switch (status.toLowerCase()) {
            case "paid" -> badge.getStyleClass().add("badge-success");
            case "cancelled", "refunded" -> badge.getStyleClass().add("badge-error");
            case "partially_paid", "partially_refunded", "draft" -> badge.getStyleClass().add("badge-warning");
            case "legacy" -> badge.getStyleClass().add("badge-neutral");
            default -> badge.getStyleClass().add("badge-neutral");
        }
        
        return badge;
    }

    /**
     * Create a transaction status badge.
     */
    public static Label createTransactionStatusBadge(String status) {
        Label badge = new Label(TextFormatter.toTitleCase(status));
        badge.getStyleClass().addAll("badge", "badge-status");
        badge.getStyleClass().add(StatusStyleMapper.getTransactionStatusClass(status));
        return badge;
    }

    /**
     * Create a flow/movement type badge.
     */
    public static Label createFlowTypeBadge(String type) {
        Label badge = new Label(type.toUpperCase());
        badge.getStyleClass().addAll("badge", "badge-status");
        
        switch (type.toLowerCase()) {
            case "sale", "out" -> badge.getStyleClass().add("badge-error");
            case "purchase", "in" -> badge.getStyleClass().add("badge-success");
            case "return" -> badge.getStyleClass().add("badge-warning");
            default -> badge.getStyleClass().add("badge-neutral");
        }
        
        return badge;
    }
}

package com.possum.ui.common.styles;

/**
 * Maps status values to CSS style classes.
 * Provides consistent styling across the application.
 */
public final class StatusStyleMapper {

    private StatusStyleMapper() {
        // Utility class
    }

    /**
     * Get CSS style class for a status value.
     * Returns appropriate badge style class.
     */
    public static String getStyleClass(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        
        return switch (status.toLowerCase()) {
            case "active", "completed", "success", "paid" -> "badge-success";
            case "inactive" -> "badge-neutral";
            case "discontinued", "pending", "draft", "partially_paid" -> "badge-warning";
            case "failed", "cancelled", "refunded" -> "badge-error";
            case "legacy" -> "badge-neutral";
            default -> "badge-neutral";
        };
    }

    /**
     * Get CSS style class for product status.
     */
    public static String getProductStatusClass(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        
        return switch (status.toLowerCase()) {
            case "active" -> "badge-success";
            case "inactive" -> "badge-neutral";
            case "discontinued" -> "badge-warning";
            default -> "badge-neutral";
        };
    }

    /**
     * Get CSS style class for transaction status.
     */
    public static String getTransactionStatusClass(String status) {
        if (status == null) {
            return "badge-neutral";
        }
        
        return switch (status.toLowerCase()) {
            case "completed", "success", "paid" -> "badge-success";
            case "pending", "draft", "partially_paid" -> "badge-warning";
            case "failed", "cancelled", "refunded" -> "badge-error";
            case "legacy" -> "badge-neutral";
            default -> "badge-neutral";
        };
    }

    /**
     * Get CSS style class for user/employee status.
     */
    public static String getUserStatusClass(boolean isActive) {
        return isActive ? "badge-success" : "badge-neutral";
    }

    /**
     * Apply status style class to a JavaFX Label.
     */
    public static void applyStatusStyle(javafx.scene.control.Label label, String status) {
        if (label == null) return;
        
        String styleClass = getStyleClass(status);
        if (!label.getStyleClass().contains(styleClass)) {
            label.getStyleClass().add(styleClass);
        }
    }

    /**
     * Apply product status style class to a JavaFX Label.
     */
    public static void applyProductStatusStyle(javafx.scene.control.Label label, String status) {
        if (label == null) return;
        
        String styleClass = getProductStatusClass(status);
        if (!label.getStyleClass().contains(styleClass)) {
            label.getStyleClass().add(styleClass);
        }
    }
}

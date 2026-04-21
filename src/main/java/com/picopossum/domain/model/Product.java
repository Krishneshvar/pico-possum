package com.picopossum.domain.model;

import java.time.LocalDateTime;

public record Product(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        java.math.BigDecimal taxRate,
        String sku,
        String barcode,
        java.math.BigDecimal mrp,
        java.math.BigDecimal costPrice,
        Integer stockAlertCap,
        String status,
        String imagePath,
        Integer stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
    public Product {
        // Enforce basic invariants for stability and rigidity
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be empty");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be empty");
        }
        if (mrp != null && mrp.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("MRP cannot be negative");
        }
        if (costPrice != null && costPrice.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost price cannot be negative");
        }
        if (taxRate != null && (taxRate.compareTo(java.math.BigDecimal.ZERO) < 0 || taxRate.compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        }
    }
}

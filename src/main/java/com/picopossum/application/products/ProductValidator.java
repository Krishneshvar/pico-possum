package com.picopossum.application.products;

import com.picopossum.domain.exceptions.ValidationException;
import java.math.BigDecimal;

/**
 * Validates product-related commands and state transitions to ensure data integrity
 * and adherence to business rules.
 */
public final class ProductValidator {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_SKU_LENGTH = 50;

    public void validateCreate(ProductService.CreateProductCommand command) {
        validateName(command.name());
        validateSku(command.sku());
        validateBarcode(command.barcode());
        validatePricing(command.mrp(), command.costPrice());
        
        if (command.stockAlertCap() != null && command.stockAlertCap() < 0) {
            throw new ValidationException("Stock alert capacity cannot be negative");
        }
        
        if (command.initialStock() != null && command.initialStock() < 0) {
            throw new ValidationException("Initial stock cannot be negative");
        }

        if (command.taxRate() != null && (command.taxRate().compareTo(java.math.BigDecimal.ZERO) < 0 || command.taxRate().compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new ValidationException("Tax rate must be between 0 and 100");
        }
    }

    public void validateUpdate(ProductService.UpdateProductCommand command) {
        if (command.name() != null) validateName(command.name());
        if (command.sku() != null) validateSku(command.sku());
        if (command.barcode() != null) validateBarcode(command.barcode());
        if (command.mrp() != null || command.costPrice() != null) {
            // If only one is provided, we'd need context of the other, 
            // but at minimum, check what we have.
            if (command.mrp() != null && command.mrp().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("MRP cannot be negative");
            }
            if (command.costPrice() != null && command.costPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Cost price cannot be negative");
            }
        }
        
        if (command.stock() != null && command.stock() < 0) {
            throw new ValidationException("Target stock cannot be negative");
        }

        if (command.taxRate() != null && (command.taxRate().compareTo(java.math.BigDecimal.ZERO) < 0 || command.taxRate().compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new ValidationException("Tax rate must be between 0 and 100");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Product name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Product name cannot exceed " + MAX_NAME_LENGTH + " characters");
        }
    }

    private void validateSku(String sku) {
        if (sku != null && sku.length() > MAX_SKU_LENGTH) {
            throw new ValidationException("SKU cannot exceed " + MAX_SKU_LENGTH + " characters");
        }
    }

    private void validateBarcode(String barcode) {
        if (barcode != null && barcode.length() > 100) {
            throw new ValidationException("Barcode cannot exceed 100 characters");
        }
    }

    private void validatePricing(BigDecimal mrp, BigDecimal costPrice) {
        if (mrp == null || mrp.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("MRP is required and must be zero or greater");
        }
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Cost price is required and must be zero or greater");
        }
        
        // Business rule: costPrice should generally not exceed mrp
        // Removing for now as some businesses might sell at loss occasionally, 
        // but it's a good candidate for a warning or strict rule.
    }
}

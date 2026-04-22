package com.picopossum.testutil;

import com.picopossum.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Modernized factory for single-user SMB POS domain objects.
 * Focused on data integrity and state changes without identity tracking overhead.
 */
public final class Fixtures {

    private Fixtures() {}

    // -------------------------------------------------------------------------
    // Customer
    // -------------------------------------------------------------------------

    public static Customer customer() {
        return new Customer(1L, "Test Customer", "9999999999", "test@example.com",
                "123 Main St", null, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    // -------------------------------------------------------------------------
    // Sale (16 fields)
    // -------------------------------------------------------------------------

    public static Sale paidSale(long id, BigDecimal total, BigDecimal paid) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid, BigDecimal.ZERO, BigDecimal.ZERO, 
                "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-00-" + id);
    }

    public static Sale paidSaleWithDiscount(long id, BigDecimal total, BigDecimal paid, BigDecimal discount) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid, BigDecimal.ZERO, discount, 
                "paid", "fulfilled", null, "Guest", null, null, "System", 1L, "Cash", "INV-00-" + id);
    }

    public static Sale cancelledSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), new BigDecimal("100.00"), 
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "cancelled", "cancelled", 
                null, "Guest", null, null, "System", 1L, "Cash", "INV-00-" + id);
    }

    public static Sale refundedSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), new BigDecimal("100.00"), 
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "refunded", "fulfilled", 
                null, "Guest", null, null, "System", 1L, "Cash", "INV-00-" + id);
    }

    // -------------------------------------------------------------------------
    // SaleItem
    // -------------------------------------------------------------------------

    public static SaleItem saleItem(long id, long saleId, long productId, int qty, String price) {
        return new SaleItem(id, saleId, productId, "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public static SaleItem saleItemWithDiscount(long id, long saleId, long productId,
                                                int qty, String price, String lineDiscount) {
        return new SaleItem(id, saleId, productId, "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(lineDiscount), BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    // -------------------------------------------------------------------------
    // Product (15 fields)
    // -------------------------------------------------------------------------

    public static Product product(long id, String name, String sku, String price) {
        return new Product(id, name, null, null, null, BigDecimal.ZERO, sku, null,
                new BigDecimal(price), new BigDecimal(price), 10, ProductStatus.ACTIVE, 
                null, 0, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static Product product(long id) {
        return product(id, "Product", "SKU-" + id, "100.00");
    }

    // -------------------------------------------------------------------------
    // User (8 fields)
    // -------------------------------------------------------------------------

    public static User activeUser(long id, String username) {
        return new User(id, "Test User", username, "hashed", true,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}

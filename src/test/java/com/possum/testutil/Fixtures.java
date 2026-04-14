package com.possum.testutil;

import com.possum.application.auth.AuthUser;
import com.possum.domain.model.*;
import com.possum.shared.dto.AvailableLot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Central factory for test domain objects.
 * All builders use sensible defaults so each test only sets what it cares about.
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

    public static Customer taxExemptCustomer() {
        return new Customer(2L, "NGO Customer", "8888888888", "ngo@example.com",
                "456 Charity Rd", null, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static Customer customerWithType(String type) {
        return new Customer(3L, "Typed Customer", "7777777777", "typed@example.com",
                "789 Trade St", type, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    // -------------------------------------------------------------------------
    // Sale
    // -------------------------------------------------------------------------

    public static Sale paidSale(long id, BigDecimal total, BigDecimal paid) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid,
                BigDecimal.ZERO, "paid", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale paidSaleWithDiscount(long id, BigDecimal total, BigDecimal paid, BigDecimal discount) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid,
                discount, "paid", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale cancelledSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, "cancelled", "cancelled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale refundedSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(),
                new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, "refunded", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // SaleItem
    // -------------------------------------------------------------------------

    public static SaleItem saleItem(long id, long saleId, long productId, int qty, String price) {
        return new SaleItem(id, saleId, productId, "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                BigDecimal.ZERO, 0);
    }

    public static SaleItem saleItemWithDiscount(long id, long saleId, long productId,
                                                int qty, String price, String lineDiscount) {
        return new SaleItem(id, saleId, productId, "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                new BigDecimal(lineDiscount), 0);
    }

    // -------------------------------------------------------------------------
    // User
    // -------------------------------------------------------------------------

    public static User activeUser(long id, String username) {
        return new User(id, "Test User", username, "hashed", true,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static User inactiveUser(long id, String username) {
        return new User(id, "Inactive User", username, "hashed", false,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static User deletedUser(long id, String username) {
        return new User(id, "Deleted User", username, "hashed", true,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // SessionRecord
    // -------------------------------------------------------------------------

    public static SessionRecord validSession(long userId, String token) {
        long expiresAt = System.currentTimeMillis() / 1000 + 1800;
        return new SessionRecord("session-id", userId, token, expiresAt, null);
    }

    public static SessionRecord expiredSession(long userId, String token) {
        long expiresAt = System.currentTimeMillis() / 1000 - 60;
        return new SessionRecord("session-id", userId, token, expiresAt, null);
    }

    // -------------------------------------------------------------------------
    // AvailableLot
    // -------------------------------------------------------------------------

    public static AvailableLot lot(long id, long productId, int remaining) {
        return new AvailableLot(id, productId, null, null, null, remaining,
                BigDecimal.ZERO, LocalDateTime.now(), remaining);
    }

    // -------------------------------------------------------------------------
    // AuthUser
    // -------------------------------------------------------------------------

    public static AuthUser authUser(long id, String username) {
        return new AuthUser(id, "Test User", username,
                List.of("admin"), List.of("sales.create", "returns.manage"));
    }

    // -------------------------------------------------------------------------
    // Role
    // -------------------------------------------------------------------------

    public static Role adminRole() {
        return new Role(1L, "admin", "Administrator");
    }

    // -------------------------------------------------------------------------
    // Product
    // -------------------------------------------------------------------------

    public static Product product(long id, String name, String sku, String price) {
        return new Product(id, name, null, null, null, sku,
                new BigDecimal(price), new BigDecimal(price), 10, "active", null, 100, null, null, null);
    }

    public static Product product(long id) {
        return product(id, "Product", "SKU-" + id, "100.00");
    }
}

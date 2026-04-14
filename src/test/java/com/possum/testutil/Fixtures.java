package com.possum.testutil;

import com.possum.application.auth.AuthUser;
import com.possum.application.sales.dto.TaxableItem;
import com.possum.domain.model.*;
import com.possum.shared.dto.AvailableLot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Central factory for test domain objects.
 * All builders use sensible defaults so each test only sets what it cares about.
 */
public final class Fixtures {

    private Fixtures() {}

    // -------------------------------------------------------------------------
    // TaxProfile
    // -------------------------------------------------------------------------

    public static TaxProfile exclusiveProfile() {
        return new TaxProfile(1L, "Standard", "IN", null, "EXCLUSIVE", true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public static TaxProfile inclusiveProfile() {
        return new TaxProfile(1L, "Standard", "IN", null, "INCLUSIVE", true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // TaxRule builder
    // -------------------------------------------------------------------------

    public static TaxRuleBuilder rule() {
        return new TaxRuleBuilder();
    }

    public static final class TaxRuleBuilder {
        private Long id = 1L;
        private Long profileId = 1L;
        private Long taxCategoryId = null;
        private BigDecimal rate = new BigDecimal("10");
        private Boolean compound = false;
        private Integer priority = 0;
        private BigDecimal minPrice = null;
        private BigDecimal maxPrice = null;
        private BigDecimal minInvoiceTotal = null;
        private BigDecimal maxInvoiceTotal = null;
        private String customerType = null;
        private LocalDate validFrom = null;
        private LocalDate validTo = null;
        private String categoryName = null;

        public TaxRuleBuilder id(long id)               { this.id = id; return this; }
        public TaxRuleBuilder rate(String rate)         { this.rate = new BigDecimal(rate); return this; }
        public TaxRuleBuilder rate(BigDecimal rate)     { this.rate = rate; return this; }
        public TaxRuleBuilder compound(boolean v)       { this.compound = v; return this; }
        public TaxRuleBuilder priority(int v)           { this.priority = v; return this; }
        public TaxRuleBuilder taxCategoryId(Long id)    { this.taxCategoryId = id; return this; }
        public TaxRuleBuilder minPrice(String v)        { this.minPrice = new BigDecimal(v); return this; }
        public TaxRuleBuilder maxPrice(String v)        { this.maxPrice = new BigDecimal(v); return this; }
        public TaxRuleBuilder minInvoiceTotal(String v) { this.minInvoiceTotal = new BigDecimal(v); return this; }
        public TaxRuleBuilder maxInvoiceTotal(String v) { this.maxInvoiceTotal = new BigDecimal(v); return this; }
        public TaxRuleBuilder customerType(String t)    { this.customerType = t; return this; }
        public TaxRuleBuilder validFrom(LocalDate d)    { this.validFrom = d; return this; }
        public TaxRuleBuilder validTo(LocalDate d)      { this.validTo = d; return this; }
        public TaxRuleBuilder categoryName(String n)    { this.categoryName = n; return this; }

        public TaxRule build() {
            return new TaxRule(id, profileId, taxCategoryId, "ITEM",
                    minPrice, maxPrice, minInvoiceTotal, maxInvoiceTotal,
                    customerType, rate, compound, priority,
                    validFrom, validTo, categoryName,
                    LocalDateTime.now(), LocalDateTime.now());
        }
    }

    // -------------------------------------------------------------------------
    // TaxableItem
    // -------------------------------------------------------------------------

    public static TaxableItem item(String price, int qty) {
        return new TaxableItem("Product", "Default", new BigDecimal(price), qty, null, 1L, 1L);
    }

    public static TaxableItem item(String price, int qty, Long taxCategoryId) {
        return new TaxableItem("Product", "Default", new BigDecimal(price), qty, taxCategoryId, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // Customer
    // -------------------------------------------------------------------------

    public static Customer customer() {
        return new Customer(1L, "Test Customer", "9999999999", "test@example.com",
                "123 Main St", null, false, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static Customer taxExemptCustomer() {
        return new Customer(2L, "NGO Customer", "8888888888", "ngo@example.com",
                "456 Charity Rd", null, true, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    public static Customer customerWithType(String type) {
        return new Customer(3L, "Typed Customer", "7777777777", "typed@example.com",
                "789 Trade St", type, false, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    // -------------------------------------------------------------------------
    // Sale
    // -------------------------------------------------------------------------

    public static Sale paidSale(long id, BigDecimal total, BigDecimal paid) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid,
                BigDecimal.ZERO, BigDecimal.ZERO, "paid", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale paidSaleWithDiscount(long id, BigDecimal total, BigDecimal paid, BigDecimal discount) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(), total, paid,
                discount, BigDecimal.ZERO, "paid", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale cancelledSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(),
                new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, "cancelled", "cancelled",
                null, 1L, null, null, null, null, null, null);
    }

    public static Sale refundedSale(long id) {
        return new Sale(id, "INV-00" + id, LocalDateTime.now(),
                new BigDecimal("100.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "refunded", "fulfilled",
                null, 1L, null, null, null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // SaleItem
    // -------------------------------------------------------------------------

    public static SaleItem saleItem(long id, long saleId, long variantId, int qty, String price) {
        return new SaleItem(id, saleId, variantId, "Variant", "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "[]", BigDecimal.ZERO, 0);
    }

    public static SaleItem saleItemWithDiscount(long id, long saleId, long variantId,
                                                int qty, String price, String lineDiscount) {
        return new SaleItem(id, saleId, variantId, "Variant", "SKU-" + id, "Product",
                qty, new BigDecimal(price), new BigDecimal(price),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "[]", new BigDecimal(lineDiscount), 0);
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

    public static AvailableLot lot(long id, long variantId, int remaining) {
        return new AvailableLot(id, variantId, null, null, null, remaining,
                BigDecimal.ZERO, null, LocalDateTime.now(), remaining);
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
    // Variant
    // -------------------------------------------------------------------------

    public static Variant variant(long id, long productId, String price) {
        return new Variant(id, productId, "Product", "Default", "SKU-" + id,
                new BigDecimal(price), new BigDecimal(price),
                null, true, "active", null, 100, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }

    // -------------------------------------------------------------------------
    // Product
    // -------------------------------------------------------------------------

    public static Product product(long id, Long taxCategoryId) {
        return new Product(id, "Product", null, null, null, taxCategoryId, null,
                "active", null, 100, LocalDateTime.now(), LocalDateTime.now(), null);
    }
}

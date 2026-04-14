package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.*;
import com.possum.domain.repositories.VariantRepository;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.db.TransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePosDraftRepository extends BaseSqliteRepository {

    private final VariantRepository variantRepository;
    private final TransactionManager transactionManager;

    public SqlitePosDraftRepository(ConnectionProvider connectionProvider, 
                                     VariantRepository variantRepository,
                                     TransactionManager transactionManager) {
        super(connectionProvider);
        this.variantRepository = variantRepository;
        this.transactionManager = transactionManager;
    }

    public void saveBill(SaleDraft draft) {
        transactionManager.runInTransaction(() -> {
            // 1. Save main bill info
            String name = draft.getCustomerName();
            String phone = draft.getCustomerPhone();
            
            executeUpdate(
                "REPLACE INTO pos_open_bills (bill_index, customer_id, customer_name, customer_phone, customer_email, customer_address, payment_method_id, overall_discount, is_discount_fixed, amount_tendered) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                draft.getIndex(),
                draft.getSelectedCustomer() != null ? draft.getSelectedCustomer().id() : null,
                name != null ? name : "",
                phone != null ? phone : "",
                draft.getCustomerEmail() != null ? draft.getCustomerEmail() : "",
                draft.getCustomerAddress() != null ? draft.getCustomerAddress() : "",
                draft.getSelectedPaymentMethod() != null ? draft.getSelectedPaymentMethod().id() : null,
                draft.getOverallDiscountValue(),
                draft.isDiscountFixed() ? 1 : 0,
                draft.getAmountTendered()
            );

            // 2. Clear and Save items
            executeUpdate("DELETE FROM pos_open_bill_items WHERE bill_index = ?", draft.getIndex());
            for (CartItem item : draft.getItems()) {
                executeUpdate(
                    "INSERT INTO pos_open_bill_items (bill_index, variant_id, quantity, price_per_unit, discount_value, discount_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    draft.getIndex(),
                    item.getVariant().id(),
                    item.getQuantity(),
                    item.getPricePerUnit(),
                    item.getDiscountValue(),
                    item.getDiscountType()
                );
            }
            return null;
        });
    }

    public Optional<SaleDraft> loadBill(int index) {
        // 1. Fetch main bill info
        Optional<SaleDraft> draftOpt = queryOne("SELECT * FROM pos_open_bills WHERE bill_index = ?", rs -> {
            SaleDraft d = new SaleDraft();
            d.setIndex(index);
            d.setCustomerName(rs.getString("customer_name") != null ? rs.getString("customer_name") : "");
            d.setCustomerPhone(rs.getString("customer_phone") != null ? rs.getString("customer_phone") : "");
            d.setCustomerEmail(rs.getString("customer_email") != null ? rs.getString("customer_email") : "");
            d.setCustomerAddress(rs.getString("customer_address") != null ? rs.getString("customer_address") : "");
            d.setOverallDiscountValue(rs.getBigDecimal("overall_discount"));
            d.setDiscountFixed(rs.getInt("is_discount_fixed") == 1);
            d.setAmountTendered(rs.getBigDecimal("amount_tendered"));
            return d;
        }, index);

        if (draftOpt.isEmpty()) return Optional.empty();
        SaleDraft draft = draftOpt.get();

        // 2. Separately restore full customer and payment method objects
        Long customerId = queryOne("SELECT customer_id FROM pos_open_bills WHERE bill_index = ?", rs -> {
            long id = rs.getLong("customer_id");
            return rs.wasNull() ? null : id;
        }, index).orElse(null);

        if (customerId != null) {
            queryOne("SELECT * FROM customers WHERE id = ?", crs -> new Customer(
                crs.getLong("id"), crs.getString("name"), crs.getString("phone"),
                crs.getString("email"), crs.getString("address"), crs.getString("customer_type"),
                crs.getInt("is_tax_exempt") == 1, null, null, null
            ), customerId).ifPresent(draft::setSelectedCustomer);
        }

        Long pmId = queryOne("SELECT payment_method_id FROM pos_open_bills WHERE bill_index = ?", rs -> {
            long id = rs.getLong("payment_method_id");
            return rs.wasNull() ? null : id;
        }, index).orElse(null);

        if (pmId != null) {
            queryOne("SELECT * FROM payment_methods WHERE id = ?", pmrs -> new PaymentMethod(
                pmrs.getLong("id"), pmrs.getString("name"), pmrs.getString("code"), pmrs.getInt("is_active") == 1
            ), pmId).ifPresent(draft::setSelectedPaymentMethod);
        }

        // 3. Load Items separately to avoid nested queries using variantRepository
        class RawItem {
            final long variantId;
            final int quantity;
            final java.math.BigDecimal pricePerUnit;
            final java.math.BigDecimal discountValue;
            final String discountType;
            RawItem(long v, int q, java.math.BigDecimal p, java.math.BigDecimal d, String t) {
                this.variantId = v; this.quantity = q; this.pricePerUnit = p; this.discountValue = d; this.discountType = t;
            }
        }

        List<RawItem> rawItems = queryList("SELECT * FROM pos_open_bill_items WHERE bill_index = ?", rs -> 
            new RawItem(rs.getLong("variant_id"), rs.getInt("quantity"), 
                      rs.getBigDecimal("price_per_unit"), rs.getBigDecimal("discount_value"), 
                      rs.getString("discount_type")), index);

        for (RawItem ri : rawItems) {
            variantRepository.findVariantByIdSync(ri.variantId).ifPresent(v -> {
                CartItem item = new CartItem(v, ri.quantity);
                item.setPricePerUnit(ri.pricePerUnit);
                item.setDiscountValue(ri.discountValue);
                item.setDiscountType(ri.discountType);
                draft.addItem(item);
            });
        }

        return Optional.of(draft);
    }

    public void deleteBill(int index) {
        executeUpdate("DELETE FROM pos_open_bills WHERE bill_index = ?", index);
    }
}

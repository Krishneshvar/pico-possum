package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.TaxCategory;
import com.possum.domain.model.TaxProfile;
import com.possum.domain.model.TaxRule;
import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.TaxCategoryMapper;
import com.possum.persistence.mappers.TaxProfileMapper;
import com.possum.persistence.mappers.TaxRuleMapper;
import com.possum.domain.repositories.TaxRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteTaxRepository extends BaseSqliteRepository implements TaxRepository {

    private final TaxProfileMapper profileMapper = new TaxProfileMapper();
    private final TaxCategoryMapper categoryMapper = new TaxCategoryMapper();
    private final TaxRuleMapper ruleMapper = new TaxRuleMapper();

    public SqliteTaxRepository(ConnectionProvider connectionProvider) {
        super(connectionProvider);
    }

    @Override
    public Optional<TaxProfile> getActiveTaxProfile() {
        return queryOne("SELECT * FROM tax_profiles WHERE is_active = 1", profileMapper);
    }

    @Override
    public List<TaxProfile> getAllTaxProfiles() {
        return queryList("SELECT * FROM tax_profiles ORDER BY created_at DESC", profileMapper);
    }

    @Override
    public long createTaxProfile(TaxProfile profile) {
        return executeInsert(
                """
                INSERT INTO tax_profiles (name, country_code, region_code, pricing_mode, is_active)
                VALUES (?, ?, ?, ?, ?)
                """,
                profile.name(),
                profile.countryCode(),
                profile.regionCode(),
                profile.pricingMode(),
                Boolean.TRUE.equals(profile.active()) ? 1 : 0
        );
    }

    @Override
    public int updateTaxProfile(long id, TaxProfile profile) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE tax_profiles SET ");
        List<String> updates = new ArrayList<>();
        if (profile.name() != null) {
            updates.add("name = ?");
            params.add(profile.name());
        }
        if (profile.countryCode() != null) {
            updates.add("country_code = ?");
            params.add(profile.countryCode());
        }
        if (profile.regionCode() != null) {
            updates.add("region_code = ?");
            params.add(profile.regionCode());
        }
        if (profile.pricingMode() != null) {
            updates.add("pricing_mode = ?");
            params.add(profile.pricingMode());
        }
        if (profile.active() != null) {
            updates.add("is_active = ?");
            params.add(profile.active() ? 1 : 0);
        }
        if (updates.isEmpty()) {
            return 0;
        }
        sql.append(String.join(", ", updates)).append(" WHERE id = ?");
        params.add(id);
        return executeUpdate(sql.toString(), params.toArray());
    }

    @Override
    public int deleteTaxProfile(long id) {
        return executeUpdate("DELETE FROM tax_profiles WHERE id = ?", id);
    }

    @Override
    public List<TaxCategory> getAllTaxCategories() {
        return queryList(
                """
                SELECT tc.*, COUNT(p.id) AS product_count
                FROM tax_categories tc
                LEFT JOIN products p ON p.tax_category_id = tc.id
                GROUP BY tc.id
                """,
                categoryMapper
        );
    }

    @Override
    public long createTaxCategory(String name, String description) {
        return executeInsert(
                "INSERT INTO tax_categories (name, description) VALUES (?, ?)",
                name,
                description
        );
    }

    @Override
    public int updateTaxCategory(long id, String name, String description) {
        return executeUpdate(
                "UPDATE tax_categories SET name = ?, description = ? WHERE id = ?",
                name,
                description,
                id
        );
    }

    @Override
    public int deleteTaxCategory(long id) {
        int usage = queryOne("SELECT COUNT(*) AS count FROM products WHERE tax_category_id = ?", rs -> rs.getInt("count"), id).orElse(0);
        if (usage > 0) {
            throw new IllegalStateException("Cannot delete category used by products");
        }
        return executeUpdate("DELETE FROM tax_categories WHERE id = ?", id);
    }

    @Override
    public List<TaxRule> getTaxRulesByProfileId(long profileId) {
        return queryList(
                """
                SELECT tr.*, tc.name AS category_name
                FROM tax_rules tr
                LEFT JOIN tax_categories tc ON tr.tax_category_id = tc.id
                WHERE tr.tax_profile_id = ?
                ORDER BY tr.priority ASC
                """,
                ruleMapper,
                profileId
        );
    }

    @Override
    public long createTaxRule(TaxRule rule) {
        return executeInsert(
                """
                INSERT INTO tax_rules (
                  tax_profile_id, tax_category_id, rule_scope, min_price, max_price, min_invoice_total, max_invoice_total,
                  customer_type, rate_percent, is_compound, priority, valid_from, valid_to
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rule.taxProfileId(),
                rule.taxCategoryId(),
                rule.ruleScope(),
                rule.minPrice(),
                rule.maxPrice(),
                rule.minInvoiceTotal(),
                rule.maxInvoiceTotal(),
                rule.customerType(),
                rule.ratePercent(),
                Boolean.TRUE.equals(rule.compound()) ? 1 : 0,
                rule.priority(),
                rule.validFrom(),
                rule.validTo()
        );
    }

    @Override
    public int updateTaxRule(long id, TaxRule rule) {
        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (rule.taxProfileId() != null) {
            updates.add("tax_profile_id = ?");
            params.add(rule.taxProfileId());
        }
        if (rule.taxCategoryId() != null) {
            updates.add("tax_category_id = ?");
            params.add(rule.taxCategoryId());
        }
        if (rule.ruleScope() != null) {
            updates.add("rule_scope = ?");
            params.add(rule.ruleScope());
        }
        if (rule.minPrice() != null) {
            updates.add("min_price = ?");
            params.add(rule.minPrice());
        }
        if (rule.maxPrice() != null) {
            updates.add("max_price = ?");
            params.add(rule.maxPrice());
        }
        if (rule.minInvoiceTotal() != null) {
            updates.add("min_invoice_total = ?");
            params.add(rule.minInvoiceTotal());
        }
        if (rule.maxInvoiceTotal() != null) {
            updates.add("max_invoice_total = ?");
            params.add(rule.maxInvoiceTotal());
        }
        if (rule.customerType() != null) {
            updates.add("customer_type = ?");
            params.add(rule.customerType());
        }
        if (rule.ratePercent() != null) {
            updates.add("rate_percent = ?");
            params.add(rule.ratePercent());
        }
        if (rule.compound() != null) {
            updates.add("is_compound = ?");
            params.add(rule.compound() ? 1 : 0);
        }
        if (rule.priority() != null) {
            updates.add("priority = ?");
            params.add(rule.priority());
        }
        if (rule.validFrom() != null) {
            updates.add("valid_from = ?");
            params.add(rule.validFrom());
        }
        if (rule.validTo() != null) {
            updates.add("valid_to = ?");
            params.add(rule.validTo());
        }
        if (updates.isEmpty()) {
            return 0;
        }
        params.add(id);
        return executeUpdate(
                "UPDATE tax_rules SET " + String.join(", ", updates) + " WHERE id = ?",
                params.toArray()
        );
    }

    @Override
    public int deleteTaxRule(long id) {
        return executeUpdate("DELETE FROM tax_rules WHERE id = ?", id);
    }
}

package com.possum.persistence.repositories.sqlite;

import com.possum.domain.model.TaxExemption;
import com.possum.domain.repositories.TaxExemptionRepository;
import com.possum.shared.util.SqlMapperUtils;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteTaxExemptionRepository implements TaxExemptionRepository {
    private final Connection connection;

    public SqliteTaxExemptionRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<TaxExemption> findById(Long id) {
        String sql = "SELECT * FROM tax_exemptions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find tax exemption", e);
        }
        return Optional.empty();
    }

    @Override
    public List<TaxExemption> findByCustomerId(Long customerId) {
        String sql = "SELECT * FROM tax_exemptions WHERE customer_id = ? ORDER BY created_at DESC";
        List<TaxExemption> exemptions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, customerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                exemptions.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find tax exemptions", e);
        }
        return exemptions;
    }

    @Override
    public Optional<TaxExemption> findActiveExemption(Long customerId, LocalDateTime asOf) {
        String sql = """
            SELECT * FROM tax_exemptions 
            WHERE customer_id = ? 
              AND valid_from <= ? 
              AND (valid_to IS NULL OR valid_to >= ?)
            ORDER BY valid_from DESC
            LIMIT 1
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, customerId);
            stmt.setString(2, asOf.toString());
            stmt.setString(3, asOf.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active tax exemption", e);
        }
        return Optional.empty();
    }

    @Override
    public TaxExemption save(TaxExemption exemption) {
        if (exemption.id() == null) {
            return insert(exemption);
        } else {
            return update(exemption);
        }
    }

    private TaxExemption insert(TaxExemption exemption) {
        String sql = """
            INSERT INTO tax_exemptions 
            (customer_id, exemption_type, certificate_number, reason, valid_from, valid_to, approved_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, exemption.customerId());
            stmt.setString(2, exemption.exemptionType());
            stmt.setString(3, exemption.certificateNumber());
            stmt.setString(4, exemption.reason());
            stmt.setString(5, exemption.validFrom() != null ? exemption.validFrom().toString() : null);
            stmt.setString(6, exemption.validTo() != null ? exemption.validTo().toString() : null);
            stmt.setLong(7, exemption.approvedBy());
            
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                return findById(keys.getLong(1)).orElseThrow();
            }
            throw new RuntimeException("Failed to get generated key");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert tax exemption", e);
        }
    }

    private TaxExemption update(TaxExemption exemption) {
        String sql = """
            UPDATE tax_exemptions 
            SET exemption_type = ?, certificate_number = ?, reason = ?, 
                valid_from = ?, valid_to = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, exemption.exemptionType());
            stmt.setString(2, exemption.certificateNumber());
            stmt.setString(3, exemption.reason());
            stmt.setString(4, exemption.validFrom() != null ? exemption.validFrom().toString() : null);
            stmt.setString(5, exemption.validTo() != null ? exemption.validTo().toString() : null);
            stmt.setLong(6, exemption.id());
            
            stmt.executeUpdate();
            return findById(exemption.id()).orElseThrow();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update tax exemption", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM tax_exemptions WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete tax exemption", e);
        }
    }

    private TaxExemption mapRow(ResultSet rs) throws SQLException {
        return new TaxExemption(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("exemption_type"),
                rs.getString("certificate_number"),
                rs.getString("reason"),
                SqlMapperUtils.getLocalDateTime(rs, "valid_from"),
                SqlMapperUtils.getLocalDateTime(rs, "valid_to"),
                rs.getLong("approved_by"),
                SqlMapperUtils.getLocalDateTime(rs, "created_at"),
                SqlMapperUtils.getLocalDateTime(rs, "updated_at")
        );
    }
}

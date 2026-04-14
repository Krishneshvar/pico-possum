package com.possum.persistence.repositories.sqlite;

import com.possum.persistence.db.ConnectionProvider;
import com.possum.persistence.mappers.RowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

public abstract class BaseSqliteRepository {

    private final ConnectionProvider connectionProvider;

    protected BaseSqliteRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    protected Connection connection() {
        return connectionProvider.getConnection();
    }

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.ofNullable(mapper.map(rs));
        } catch (SQLException ex) {
            throw DatabaseExceptionTranslator.translate("Failed queryOne for SQL: " + sql, ex);
        }
    }

    protected <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet rs = statement.executeQuery()) {
            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapper.map(rs));
            }
            return results;
        } catch (SQLException ex) {
            throw DatabaseExceptionTranslator.translate("Failed queryList for SQL: " + sql, ex);
        }
    }

    protected long executeInsert(String sql, Object... params) {
        try (PreparedStatement statement = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1L;
        } catch (SQLException ex) {
            throw DatabaseExceptionTranslator.translate("Failed executeInsert for SQL: " + sql, ex);
        }
    }

    protected int executeUpdate(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw DatabaseExceptionTranslator.translate("Failed executeUpdate for SQL: " + sql, ex);
        }
    }

    protected PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection().prepareStatement(sql);
        bind(statement, params);
        return statement;
    }

    protected static void bind(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    // Common utility methods

    /**
     * Build dynamic UPDATE SQL with only non-null fields.
     * Table name is validated to prevent SQL injection.
     */
    protected static class UpdateBuilder {
        private final StringBuilder sql;
        private final List<Object> params = new ArrayList<>();
        private boolean hasFields = false;

        public UpdateBuilder(String tableName) {
            validateTableName(tableName);
            this.sql = new StringBuilder("UPDATE " + tableName + " SET updated_at = CURRENT_TIMESTAMP");
        }

        public UpdateBuilder set(String column, Object value) {
            if (value != null) {
                validateColumnName(column);
                sql.append(", ").append(column).append(" = ?");
                params.add(value);
                hasFields = true;
            }
            return this;
        }

        public UpdateBuilder where(String condition, Object... values) {
            sql.append(" WHERE ").append(condition);
            for (Object value : values) {
                params.add(value);
            }
            return this;
        }

        public String getSql() {
            return sql.toString();
        }

        public Object[] getParams() {
            return params.toArray();
        }

        public boolean hasFields() {
            return hasFields;
        }
        
        private static void validateTableName(String tableName) {
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("Table name cannot be null or empty");
            }
            if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                throw new IllegalArgumentException("Invalid table name: " + tableName);
            }
        }
        
        private static void validateColumnName(String column) {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("Column name cannot be null or empty");
            }
            if (!column.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                throw new IllegalArgumentException("Invalid column name: " + column);
            }
        }
    }

    /**
     * Build dynamic WHERE clause with search terms.
     * All column names are validated to prevent SQL injection.
     */
    protected static class WhereBuilder {
        private final StringJoiner joiner = new StringJoiner(" AND ");
        private final List<Object> params = new ArrayList<>();

        public WhereBuilder addNotDeleted() {
            return addNotDeleted(null);
        }

        public WhereBuilder addNotDeleted(String alias) {
            String column = validateColumnName(alias != null && !alias.isBlank() ? alias + ".deleted_at" : "deleted_at");
            joiner.add(column + " IS NULL");
            return this;
        }

        public WhereBuilder addSearch(String searchTerm, String... columns) {
            if (searchTerm != null && !searchTerm.isBlank()) {
                String trimmed = searchTerm.trim();
                StringJoiner orJoiner = new StringJoiner(" OR ");
                for (String column : columns) {
                    String validColumn = validateColumnName(column);
                    orJoiner.add(validColumn + " LIKE ?");
                    params.add("%" + trimmed + "%");
                }
                joiner.add("(" + orJoiner + ")");
            }
            return this;
        }

        public WhereBuilder addIn(String column, List<?> values) {
            if (values != null && !values.isEmpty()) {
                String validColumn = validateColumnName(column);
                String placeholders = "?,".repeat(values.size()).replaceAll(",$", "");
                joiner.add(validColumn + " IN (" + placeholders + ")");
                params.addAll(values);
            }
            return this;
        }

        public WhereBuilder addCondition(String condition, Object... values) {
            joiner.add(condition);
            for (Object value : values) {
                params.add(value);
            }
            return this;
        }

        public String build() {
            return joiner.length() > 0 ? "WHERE " + joiner : "";
        }

        public List<Object> getParams() {
            return params;
        }
        
        private static String validateColumnName(String column) {
            if (column == null || column.isBlank()) {
                throw new IllegalArgumentException("Column name cannot be null or empty");
            }
            if (!column.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$")) {
                throw new IllegalArgumentException("Invalid column name: " + column);
            }
            return column;
        }
    }

    /**
     * Execute soft delete on a table.
     */
    protected int softDelete(String tableName, long id) {
        return executeUpdate(
                "UPDATE " + tableName + " SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL",
                id
        );
    }

    /**
     * Count records with WHERE clause.
     */
    protected int count(String tableName, String whereClause, Object... params) {
        return queryOne(
                "SELECT COUNT(*) AS count FROM " + tableName + " " + whereClause,
                rs -> rs.getInt("count"),
                params
        ).orElse(0);
    }

    /**
     * Parse SQLite datetime string to LocalDateTime.
     */
    protected static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.replace(' ', 'T'));
    }

    /**
     * Convert Boolean to int for SQLite.
     */
    protected static int boolToInt(Boolean value, boolean defaultValue) {
        boolean resolved = value == null ? defaultValue : value;
        return resolved ? 1 : 0;
    }
}

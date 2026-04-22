package com.picopossum.persistence.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Objects;
import java.util.function.Supplier;

public final class TransactionManager {

    private final ConnectionProvider connectionProvider;

    public TransactionManager(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    public <T> T runInTransaction(Supplier<T> block) {
        Objects.requireNonNull(block, "block must not be null");

        Connection connection = connectionProvider.getConnection();
        boolean isOuterTransaction = getAutoCommit(connection);

        if (isOuterTransaction) {
            // Outermost transaction: manage lifecycle and binding
            if (connectionProvider instanceof DatabaseManager dbm) {
                dbm.bindConnection(connection);
            }
            try {
                return com.picopossum.shared.util.RetryUtil.executeWithRetry(() -> runOutermost(connection, block));
            } finally {
                if (connectionProvider instanceof DatabaseManager dbm) {
                    dbm.unbindConnection();
                }
                closeQuietly(connection);
            }
        } else {
            // Nested transaction: reuse existing connection and binding
            return runNested(connection, block);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ex) {
            com.picopossum.infrastructure.logging.LoggingConfig.getLogger().error("Failed to close transaction connection", ex);
        }
    }

    private static boolean getAutoCommit(Connection connection) {
        try {
            return connection.getAutoCommit();
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read auto-commit state", ex);
        }
    }

    private static <T> T runOutermost(Connection connection, Supplier<T> block) {
        Throwable failure = null;

        try {
            connection.setAutoCommit(false);
            T result = block.get();
            connection.commit();
            return result;
        } catch (RuntimeException | Error ex) {
            failure = ex;
            rollback(connection, ex);
            throw ex;
        } catch (SQLException ex) {
            failure = ex;
            rollback(connection, ex);
            throw new IllegalStateException("Transaction commit failed", ex);
        } finally {
            restoreAutoCommit(connection, true, failure);
        }
    }

    private static <T> T runNested(Connection connection, Supplier<T> block) {
        Savepoint savepoint = createSavepoint(connection);
        try {
            T result = block.get();
            releaseSavepoint(connection, savepoint);
            return result;
        } catch (RuntimeException | Error ex) {
            rollbackToSavepoint(connection, savepoint, ex);
            throw ex;
        }
    }

    private static Savepoint createSavepoint(Connection connection) {
        try {
            return connection.setSavepoint();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create transaction savepoint", ex);
        }
    }

    private static void rollbackToSavepoint(Connection connection, Savepoint savepoint, Throwable primaryFailure) {
        try {
            connection.rollback(savepoint);
        } catch (SQLException ex) {
            primaryFailure.addSuppressed(ex);
        } finally {
            releaseSavepoint(connection, savepoint);
        }
    }

    private static void releaseSavepoint(Connection connection, Savepoint savepoint) {
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLException ignored) {
            // Savepoint release can fail after rollback on some drivers; transaction remains valid.
        }
    }

    private static void rollback(Connection connection, Throwable primaryFailure) {
        try {
            connection.rollback();
        } catch (SQLException ex) {
            primaryFailure.addSuppressed(ex);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean previousAutoCommit, Throwable primaryFailure) {
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException ex) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(ex);
                return;
            }
            throw new IllegalStateException("Failed to restore auto-commit mode", ex);
        }
    }
}

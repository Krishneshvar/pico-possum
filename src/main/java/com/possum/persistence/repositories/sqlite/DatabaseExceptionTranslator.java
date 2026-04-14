package com.possum.persistence.repositories.sqlite;

import com.possum.domain.exceptions.DatabaseBusyException;
import com.possum.domain.exceptions.DatabaseConflictException;
import com.possum.domain.exceptions.DatabaseException;
import java.sql.SQLException;

/**
 * Translates SQLite-specific SQL exceptions into domain-specific exceptions.
 */
public final class DatabaseExceptionTranslator {

    private DatabaseExceptionTranslator() {}

    public static RuntimeException translate(String message, SQLException ex) {
        int errorCode = ex.getErrorCode();

        // SQLite error codes: https://www.sqlite.org/rescode.html
        // 5 = SQLITE_BUSY, 6 = SQLITE_LOCKED, 261 = SQLITE_BUSY_RECOVERY, etc.
        if (errorCode == 5 || errorCode == 6 || errorCode == 261) {
            return new DatabaseBusyException("Database is currently busy or locked. Please try again.", ex);
        }

        // 19 = SQLITE_CONSTRAINT, 2067 = SQLITE_CONSTRAINT_UNIQUE, etc.
        if (errorCode == 19 || (errorCode >= 2000 && errorCode <= 3000)) {
            if (ex.getMessage() != null && (ex.getMessage().contains("UNIQUE") || ex.getMessage().contains("PRIMARY KEY"))) {
                return new DatabaseConflictException("A record with this information already exists.", ex);
            }
            return new DatabaseConflictException("A data integrity constraint was violated: " + ex.getMessage(), ex);
        }

        return new DatabaseException(message + ": " + ex.getMessage(), ex);
    }
}

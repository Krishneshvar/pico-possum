package com.possum.domain.exceptions;

/**
 * Thrown when the database is busy or locked (SQLITE_BUSY).
 * This is usually a transient error that can be retried.
 */
public class DatabaseBusyException extends DatabaseException {
    public DatabaseBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}

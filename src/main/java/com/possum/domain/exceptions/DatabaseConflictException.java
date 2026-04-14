package com.possum.domain.exceptions;

/**
 * Thrown when a database operation violates a constraint (e.g., UNIQUE).
 */
public class DatabaseConflictException extends DatabaseException {
    public DatabaseConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}

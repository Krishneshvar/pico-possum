package com.possum.domain.exceptions;

/**
 * Base class for all database-related exceptions.
 */
public class DatabaseException extends DomainException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

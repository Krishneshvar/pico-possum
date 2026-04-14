package com.possum.domain.exceptions;

/**
 * Thrown when data read from storage is corrupt or unreadable.
 */
public class DataCorruptionException extends DomainException {
    public DataCorruptionException(String message) {
        super(message);
    }

    public DataCorruptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

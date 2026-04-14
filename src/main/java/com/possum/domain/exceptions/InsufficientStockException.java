package com.possum.domain.exceptions;

public class InsufficientStockException extends DomainException {
    private final int available;
    private final int requested;

    public InsufficientStockException(int available, int requested) {
        super(String.format("Insufficient stock. Available: %d, Requested: %d", available, requested));
        this.available = available;
        this.requested = requested;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}

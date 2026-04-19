package com.picopossum.domain.exceptions;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message);
    }
}

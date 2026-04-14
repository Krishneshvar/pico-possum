package com.possum.ui.common;

import com.possum.domain.exceptions.*;

public final class ErrorHandler {

    private ErrorHandler() {}

    public static String toUserMessage(Throwable e) {
        if (e instanceof ValidationException) {
            return e.getMessage();
        }
        if (e instanceof NotFoundException) {
            return e.getMessage();
        }
        if (e instanceof InsufficientStockException) {
            return e.getMessage();
        }
        if (e instanceof AuthenticationException) {
            return e.getMessage();
        }
        if (e instanceof AuthorizationException) {
            return "You don't have permission to perform this action.";
        }
        if (e instanceof DatabaseConflictException || e instanceof DatabaseBusyException) {
            return e.getMessage();
        }
        if (e instanceof DomainException) {
            return e.getMessage();
        }
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        // Unwrap cause for runtime wrappers
        if (e.getCause() != null && e.getCause() != e) {
            return toUserMessage(e.getCause());
        }
        return "An unexpected error occurred. Please try again.";
    }
}

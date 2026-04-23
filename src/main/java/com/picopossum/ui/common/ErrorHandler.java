package com.picopossum.ui.common;

import com.picopossum.domain.exceptions.*;

public final class ErrorHandler {

    private ErrorHandler() {}

    public static String toUserMessage(Throwable e) {
        if (e == null) {
            return "An unknown error occurred.";
        }

        // Unwrap common wrapper exceptions
        Throwable current = e;
        while (current != null && (
               current instanceof java.lang.reflect.InvocationTargetException ||
               current instanceof java.lang.RuntimeException && current.getMessage() == null && current.getCause() != null)) {
            if (current.getCause() == null || current.getCause() == current) break;
            current = current.getCause();
        }

        // Check if it's a known domain exception
        if (current instanceof ValidationException || 
            current instanceof NotFoundException || 
            current instanceof InsufficientStockException || 
            current instanceof AuthenticationException || 
            current instanceof DatabaseConflictException || 
            current instanceof DatabaseBusyException || 
            current instanceof DomainException || 
            current instanceof IllegalArgumentException) {
            
            String msg = current.getMessage();
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        }

        // Generic search for any message in the cause chain
        Throwable search = current;
        while (search != null) {
            String msg = search.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals(search.getClass().getName())) {
                return msg;
            }
            if (search.getCause() == search) break;
            search = search.getCause();
        }

        // Last resort fallback with class name for easier debugging
        return "A validation or data error occurred (" + current.getClass().getSimpleName() + ")";
    }
}

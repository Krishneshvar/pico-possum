package com.possum.shared.util;

import com.possum.domain.exceptions.DatabaseBusyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Utility for retrying operations that may fail due to transient errors.
 */
public final class RetryUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtil.class);
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 100;

    private RetryUtil() {}

    /**
     * Executes a supplier with a retry mechanism for DatabaseBusyException.
     */
    public static <T> T executeWithRetry(Supplier<T> action) {
        int attempt = 0;
        while (true) {
            try {
                return action.get();
            } catch (DatabaseBusyException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    LOGGER.error("Operation failed after {} retries due to database busy.", MAX_RETRIES);
                    throw e;
                }
                long delay = INITIAL_DELAY_MS * (long) Math.pow(2, attempt - 1);
                LOGGER.warn("Database busy. Retrying in {}ms (Attempt {}/{})", delay, attempt, MAX_RETRIES);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }
    }
}

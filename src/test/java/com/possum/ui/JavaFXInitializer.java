package com.possum.ui;

import javafx.application.Platform;
import java.util.concurrent.CountDownLatch;

public class JavaFXInitializer {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) return;
        
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
            initialized = true;
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            initialized = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}

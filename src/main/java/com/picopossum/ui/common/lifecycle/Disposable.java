package com.picopossum.ui.common.lifecycle;

/**
 * Interface for UI controllers that need to clean up resources
 * (background threads, event listeners, etc.) when they are closed.
 */
public interface Disposable {
    /**
     * Called when the UI component is being permanently removed/closed.
     */
    void dispose();
}
